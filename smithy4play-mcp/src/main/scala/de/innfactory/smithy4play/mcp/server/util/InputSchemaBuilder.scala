package de.innfactory.smithy4play.mcp.server.util

import alloy.Untagged
import play.api.Logging
import smithy.api.{ Default, Documentation, HttpLabel, HttpPayload, HttpQuery, Length, Pattern, Range }
import smithy4s.schema.{ Alt, Field, Primitive, SchemaVisitor }
import smithy4s.{ Document, Hints, Schema, ShapeId }
import de.innfactory.smithy4play.mcp.server.util.DocumentUtils.merge

import scala.collection.mutable

object InputSchemaBuilder extends Logging {

  private case class SchemaInfo[A](document: Document, isOptional: Boolean)

  def build[A](schema: Schema[A]): Document = {
    logger.debug("Creating JSON Schema for input validation.")
    val schemaInfo = schema.compile(SchemaToJsonSchemaVisitor)
    schemaInfo.document
  }

  private object SchemaToJsonSchemaVisitor extends SchemaVisitor[SchemaInfo] with Logging { self =>

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): SchemaInfo[P] = {
      val baseType = tag match {
        case Primitive.PInt | Primitive.PShort | Primitive.PLong | Primitive.PByte            => "integer"
        case Primitive.PFloat | Primitive.PDouble | Primitive.PBigDecimal | Primitive.PBigInt => "number"
        case Primitive.PBoolean                                                               => "boolean"
        case Primitive.PString | Primitive.PUUID | Primitive.PBlob | Primitive.PDocument      => "string"
        case Primitive.PTimestamp                                                             => "string"
      }

      val base = Document.obj("type" -> Document.fromString(baseType))

      val withFormat = tag match {
        case Primitive.PTimestamp => merge(base, Document.obj("format" -> Document.fromString("date-time")))
        case Primitive.PUUID      => merge(base, Document.obj("format" -> Document.fromString("uuid")))
        case _                    => base
      }

      val withDefault = hints.get[Default] match {
        case Some(default) => merge(withFormat, Document.obj("default" -> default.value))
        case None          => withFormat
      }

      val result = applyConstraints(withDefault, hints)

      SchemaInfo(result, isOptional = false)
    }

    override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: smithy4s.schema.CollectionTag[C],
      member: Schema[A]
    ): SchemaInfo[C[A]] = {
      val memberInfo = member.compile(this)
      SchemaInfo(
        Document.obj(
          "type"  -> Document.fromString("array"),
          "items" -> memberInfo.document
        ),
        isOptional = false
      )
    }

    override def map[K, V](shapeId: ShapeId, hints: Hints, key: Schema[K], value: Schema[V]): SchemaInfo[Map[K, V]] = {
      val valueInfo = value.compile(this)
      SchemaInfo(
        Document.obj(
          "type"                 -> Document.fromString("object"),
          "additionalProperties" -> valueInfo.document
        ),
        isOptional = false
      )
    }

    override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: smithy4s.schema.EnumTag[E],
      values: List[smithy4s.schema.EnumValue[E]],
      total: E => smithy4s.schema.EnumValue[E]
    ): SchemaInfo[E] =
      SchemaInfo(
        Document.obj(
          "type" -> Document.fromString("string"),
          "enum" -> Document.array(values.map(v => Document.fromString(v.name))*)
        ),
        isOptional = false
      )

    override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, ?]],
      make: IndexedSeq[Any] => S
    ): SchemaInfo[S] = {
      val properties = mutable.Map[String, Document]()
      val required   = mutable.ListBuffer[String]()

      fields.foreach { field =>
        val fieldInfo  = field.schema.compile(this)
        val fieldHints = field.hints

        val isLabel    = fieldHints.get[HttpLabel].isDefined
        val isPayload  = fieldHints.get[HttpPayload].isDefined
        val queryParam = fieldHints.get[HttpQuery].map(_.value)

        val withLocation = if (isLabel) {
          merge(fieldInfo.document, Document.obj("in" -> Document.fromString("label")))
        } else if (isPayload) {
          merge(fieldInfo.document, Document.obj("in" -> Document.fromString("payload")))
        } else if (queryParam.isDefined) {
          merge(fieldInfo.document, Document.obj("in" -> Document.fromString("query")))
        } else {
          fieldInfo.document
        }

        val descriptionParts = scala.collection.mutable.ListBuffer[String]()

        fieldHints.get[Documentation].foreach { doc =>
          descriptionParts += doc.value
        }

        fieldHints.get[Pattern].foreach { pattern =>
          descriptionParts += s"Pattern: ${pattern.value}"
        }

        fieldHints.get[Length].foreach { length =>
          val constraints = scala.collection.mutable.ListBuffer[String]()
          length.min.foreach(min => constraints += s"min length: $min")
          length.max.foreach(max => constraints += s"max length: $max")
          if (constraints.nonEmpty) {
            descriptionParts += s"Length constraints: ${constraints.mkString(", ")}"
          }
        }

        fieldHints.get[Range].foreach { range =>
          val constraints = scala.collection.mutable.ListBuffer[String]()
          range.min.foreach(min => constraints += s"min: $min")
          range.max.foreach(max => constraints += s"max: $max")
          if (constraints.nonEmpty) {
            descriptionParts += s"Range constraints: ${constraints.mkString(", ")}"
          }
        }

        val withDescription = if (descriptionParts.nonEmpty) {
          merge(
            withLocation,
            Document.obj("description" -> Document.fromString(descriptionParts.mkString(" | ")))
          )
        } else {
          merge(
            withLocation,
            Document.obj("description" -> Document.fromString(s"Field ${field.label}"))
          )
        }

        properties(field.label) = withDescription

        if (!fieldInfo.isOptional) {
          required += field.label
        }
      }

      val base = Document.obj(
        "type"       -> Document.fromString("object"),
        "properties" -> Document.obj(properties.toSeq*)
      )

      val result = if (required.nonEmpty) {
        merge(base, Document.obj("required" -> Document.array(required.map(Document.fromString).toSeq*)))
      } else {
        base
      }

      SchemaInfo(result, isOptional = false)
    }

    private def buildTaggedUnionSchema[U](
      shapeId: ShapeId,
      alternatives: Vector[Alt[U, ?]]
    ): SchemaInfo[U] = {
      val variants = alternatives.map { alt =>
        val variantInfo = alt.schema.compile(this)

        val wrappedVariant = Document.obj(
          "type"                 -> Document.fromString("object"),
          "title"                -> Document.fromString(alt.label),
          "description"          -> Document.fromString(
            s"Union variant '${alt.label}' - must be wrapped in an object with key '${alt.label}'"
          ),
          "properties"           -> Document.obj(
            alt.label -> variantInfo.document
          ),
          "required"             -> Document.array(Document.fromString(alt.label)),
          "additionalProperties" -> Document.fromBoolean(false)
        )
        wrappedVariant
      }

      SchemaInfo(
        Document.obj(
          "type"            -> Document.fromString("object"),
          "description"     -> Document.fromString(
            s"Tagged union for ${shapeId.name}. " +
              s"Must be an object with exactly ONE key from: ${alternatives.map(_.label).mkString(", ")}. " +
              s"The key determines which variant is used, and its value must match that variant's schema."
          ),
          "oneOf"           -> Document.array(variants*),
          "x-discriminator" -> Document.fromString("The object key determines the variant type"),
          "x-usage"         -> Document.fromString(
            "To create this union, pass an object with a single key matching one of the variant names. " +
              "To create this union, pass an object with the base key" +
              "For example: {'basic': {...fields...}} or {'appointment': {...fields...}}"
          )
        ),
        isOptional = false
      )
    }

    private def buildUntaggedUnionSchema[U](
      shapeId: ShapeId,
      alternatives: Vector[Alt[U, ?]]
    ): SchemaInfo[U] = {
      val variants = alternatives.map { alt =>
        val variantInfo = alt.schema.compile(this)

        val wrappedVariant = merge(
          Document.obj(
            "type"                 -> Document.fromString("object"),
            "title"                -> Document.fromString(alt.label),
            "description"          -> Document.fromString(
              s"untagged union variant '${alt.label}' - does not require additional wrapping, must match the schema directly'"
            ),
            "required"             -> Document.array(Document.fromString(alt.label)),
            "additionalProperties" -> Document.fromBoolean(false)
          ),
          variantInfo.document
        )
        wrappedVariant
      }

      SchemaInfo(
        Document.obj(
          "oneOf"       -> Document.array(variants*),
          "description" -> Document.fromString(
            s"Untagged union for ${shapeId.name}. Must match exactly one of the variants."
          ),
          "x-usage"     -> Document.fromString(
            s"To create this union, pass in all fields contained in the union object" +
              s"For example: { ...fieldsOfBasic } or { ...fieldsOfAppointment }"
          )
        ),
        isOptional = false
      )
    }

    private def buildDiscriminatedUnionSchema[U](
      shapeId: ShapeId,
      alternatives: Vector[Alt[U, ?]],
      disc: alloy.Discriminated
    ): SchemaInfo[U] = {
      val variants = alternatives.map { alt =>
        val variantInfo = alt.schema.compile(this)

        val tpeInfo = Document.obj(
          disc.value -> Document.fromString(alt.label)
        )

        val wrappedVariant = Document.obj(
          "type"                 -> Document.fromString("object"),
          "title"                -> Document.fromString(shapeId.name),
          "description"          -> Document.fromString(
            s"Discriminated union for ${shapeId.name}. Discriminator field '${disc.value}' determines the variant and must match exactly one of the variant names. The union object does contain the discriminator filed along with the variant fields. Union variant '${alt.label}' - must be labeled through discriminator ${disc.value} as key and value '${alt.label}'" +
              s"To create this union, pass an object with the discriminator ${disc.value} as key and the variant name as value" +
              s"For example: { '${disc.value}': 'basic', ...fieldsOfBasic } or { '${disc.value}': 'appointment', ...fieldsOfAppointment }"
          ),
          "properties"           -> merge(
            variantInfo.document match {
              case Document.DObject(value) =>
                value("properties")
              case _                       => Document.obj()
            },
            Document.obj(
              disc.value -> Document.fromString(alt.label)
            )
          ),
          "required"             -> Document.array(Document.fromString(disc.value)),
          "additionalProperties" -> Document.fromBoolean(false)
        )
        wrappedVariant
      }

      SchemaInfo(
        Document.obj(
          "oneOf"           -> Document.array(variants*),
          "description"     -> Document.fromString(
            s"Discriminated union for ${shapeId.name}. Discriminator field '${disc.value}' determines the variant and must match exactly one of the variant names. The union object does contain the discriminator filed along with the variant fields."
          ),
          "x-discriminator" -> Document.fromString("The field '" + disc.value + "' determines the variant type"),
          "x-usage"         -> Document.fromString(
            s"To create this union, pass an object with the discriminator ${disc.value} as key and the variant name as value" +
              s"For example: { '${disc.value}': 'basic', ...fieldsOfBasic } or { '${disc.value}': 'appointment', ...fieldsOfAppointment }"
          )
        ),
        isOptional = false
      )
    }

    override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, ?]],
      dispatch: Alt.Dispatcher[U]
    ): SchemaInfo[U] = {
      logger.debug(
        s"Building union schema for ${shapeId.name} with alternatives: ${alternatives.map(_.label).mkString(", ")}, hints: ${hints.all
            .mkString(", ")} "
      )

      val untagged      = hints.get[Untagged]
      val discriminated = hints.get(alloy.Discriminated)

      (untagged, discriminated) match {
        case (None, Some(disc)) => buildDiscriminatedUnionSchema(shapeId, alternatives, disc)
        case (Some(_), None)    => buildUntaggedUnionSchema(shapeId, alternatives)
        case _                  => buildTaggedUnionSchema(shapeId, alternatives)
      }
    }

    override def biject[A, B](schema: Schema[A], bijection: smithy4s.Bijection[A, B]): SchemaInfo[B] =
      schema.compile(this).asInstanceOf[SchemaInfo[B]]

    override def refine[A, B](schema: Schema[A], refinement: smithy4s.Refinement[A, B]): SchemaInfo[B] =
      schema.compile(this).asInstanceOf[SchemaInfo[B]]

    override def lazily[A](suspend: smithy4s.Lazy[Schema[A]]): SchemaInfo[A] =
      suspend.value.compile(this)

    override def option[A](schema: Schema[A]): SchemaInfo[Option[A]] = {
      val innerInfo = schema.compile(this)
      SchemaInfo(innerInfo.document, isOptional = true)
    }
  }

  private def applyConstraints(document: Document, hints: Hints): Document = {
    var result      = document
    val constraints = mutable.Map[String, Document]()

    // Add pattern constraint
    hints.get[Pattern].foreach { pattern =>
      constraints("pattern") = Document.fromString(pattern.value)
    }

    // Add length constraints
    hints.get[Length].foreach { length =>
      length.min.foreach(min => constraints("minLength") = Document.fromString(min.toString))
      length.max.foreach(max => constraints("maxLength") = Document.fromString(max.toString))
    }

    // Add range constraints
    hints.get[Range].foreach { range =>
      range.min.foreach(min => constraints("minimum") = Document.fromString(min.toString))
      range.max.foreach(max => constraints("maximum") = Document.fromString(max.toString))
    }

    // Add documentation
    hints.get[Documentation].foreach { doc =>
      constraints("description") = Document.fromString(doc.value)
    }

    if (constraints.nonEmpty) {
      result = merge(result, Document.obj(constraints.toSeq*))
    }

    result
  }

}
