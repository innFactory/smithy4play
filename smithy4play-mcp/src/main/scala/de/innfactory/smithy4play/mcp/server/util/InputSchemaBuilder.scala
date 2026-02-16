package de.innfactory.smithy4play.mcp.server.util

import alloy.Untagged
import smithy.api.{ Default, Documentation, Length, Pattern, Range }
import smithy4s.schema.{ Alt, Field, Primitive, SchemaVisitor }
import smithy4s.{ Document, Hints, Schema, ShapeId }
import de.innfactory.smithy4play.mcp.server.util.DocumentUtils.merge
import de.innfactory.smithy4play.mcp.server.util.SchemaUtils.primitiveToJsonType

object InputSchemaBuilder {

  private case class SchemaInfo[A](document: Document, isOptional: Boolean)

  def build[A](schema: Schema[A]): Document =
    schema.compile(SchemaToJsonSchemaVisitor).document

  private object SchemaToJsonSchemaVisitor extends SchemaVisitor[SchemaInfo] {

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): SchemaInfo[P] = {
      val base = Document.obj("type" -> Document.fromString(primitiveToJsonType(tag)))

      val withFormat = tag match {
        case Primitive.PTimestamp => merge(base, Document.obj("format" -> Document.fromString("date-time")))
        case Primitive.PUUID      => merge(base, Document.obj("format" -> Document.fromString("uuid")))
        case _                    => base
      }

      val withDefault = hints.get[Default] match {
        case Some(default) => merge(withFormat, Document.obj("default" -> default.value))
        case None          => withFormat
      }

      SchemaInfo(applyConstraints(withDefault, hints), isOptional = false)
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
      val properties = scala.collection.mutable.Map[String, Document]()
      val required   = scala.collection.mutable.ListBuffer[String]()

      fields.foreach { field =>
        val fieldInfo  = field.schema.compile(this)
        val fieldHints = field.hints

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
          if (constraints.nonEmpty) descriptionParts += s"Length constraints: ${constraints.mkString(", ")}"
        }

        fieldHints.get[Range].foreach { range =>
          val constraints = scala.collection.mutable.ListBuffer[String]()
          range.min.foreach(min => constraints += s"min: $min")
          range.max.foreach(max => constraints += s"max: $max")
          if (constraints.nonEmpty) descriptionParts += s"Range constraints: ${constraints.mkString(", ")}"
        }

        val withDescription =
          if (descriptionParts.nonEmpty)
            merge(
              fieldInfo.document,
              Document.obj("description" -> Document.fromString(descriptionParts.mkString(" | ")))
            )
          else
            fieldInfo.document

        properties(field.label) = withDescription

        if (!fieldInfo.isOptional) required += field.label
      }

      val base = Document.obj(
        "type"       -> Document.fromString("object"),
        "properties" -> Document.obj(properties.toSeq*)
      )

      val result =
        if (required.nonEmpty)
          merge(base, Document.obj("required" -> Document.array(required.map(Document.fromString).toSeq*)))
        else base

      SchemaInfo(result, isOptional = false)
    }

    private def buildTaggedUnionSchema[U](
      shapeId: ShapeId,
      alternatives: Vector[Alt[U, ?]]
    ): SchemaInfo[U] = {
      val variants = alternatives.map { alt =>
        val variantInfo = alt.schema.compile(this)
        Document.obj(
          "type"                 -> Document.fromString("object"),
          "title"                -> Document.fromString(alt.label),
          "description"          -> Document.fromString(s"Union variant '${alt.label}' - wrap with key '${alt.label}'"),
          "properties"           -> Document.obj(alt.label -> variantInfo.document),
          "required"             -> Document.array(Document.fromString(alt.label)),
          "additionalProperties" -> Document.fromBoolean(false)
        )
      }

      SchemaInfo(
        Document.obj(
          "type"            -> Document.fromString("object"),
          "description"     -> Document.fromString(
            s"Tagged union: one key from ${alternatives.map(_.label).mkString(", ")}"
          ),
          "oneOf"           -> Document.array(variants*),
          "x-discriminator" -> Document.fromString("Object key determines variant"),
          "x-usage"         -> Document.fromString(
            s"Pass object with single key: ${alternatives.map(a => s"{'${a.label}': ...}").mkString(" or ")}"
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
        merge(
          Document.obj(
            "type"                 -> Document.fromString("object"),
            "title"                -> Document.fromString(alt.label),
            "description"          -> Document.fromString(s"Untagged variant '${alt.label}'"),
            "required"             -> Document.array(Document.fromString(alt.label)),
            "additionalProperties" -> Document.fromBoolean(false)
          ),
          variantInfo.document
        )
      }

      SchemaInfo(
        Document.obj(
          "oneOf"       -> Document.array(variants*),
          "description" -> Document.fromString(
            s"Untagged union: match one of ${alternatives.map(_.label).mkString(", ")}"
          ),
          "x-usage"     -> Document.fromString("Pass fields directly without wrapper")
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
        val variantInfo  = alt.schema.compile(this)
        val variantProps = variantInfo.document match {
          case Document.DObject(value) => value.get("properties").getOrElse(Document.obj())
          case _                       => Document.obj()
        }

        Document.obj(
          "type"                 -> Document.fromString("object"),
          "title"                -> Document.fromString(alt.label),
          "description"          -> Document.fromString(s"Variant '${alt.label}' (${disc.value}=${alt.label})"),
          "properties"           -> merge(
            variantProps,
            Document.obj(disc.value -> Document.obj("const" -> Document.fromString(alt.label)))
          ),
          "required"             -> Document.array(Document.fromString(disc.value)),
          "additionalProperties" -> Document.fromBoolean(false)
        )
      }

      SchemaInfo(
        Document.obj(
          "oneOf"           -> Document.array(variants*),
          "description"     -> Document.fromString(s"Discriminated union on '${disc.value}'"),
          "x-discriminator" -> Document.fromString(disc.value),
          "x-usage"         -> Document.fromString(s"Include '${disc.value}' field with variant name")
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
      val untagged      = hints.get[Untagged]
      val discriminated = hints.get(alloy.Discriminated)

      (untagged, discriminated) match {
        case (None, Some(disc)) => buildDiscriminatedUnionSchema(shapeId, alternatives, disc)
        case (Some(_), None)    => buildUntaggedUnionSchema(shapeId, alternatives)
        case _                  => buildTaggedUnionSchema(shapeId, alternatives)
      }
    }

    override def biject[A, B](schema: Schema[A], bijection: smithy4s.Bijection[A, B]): SchemaInfo[B] = {
      val info = schema.compile(this)
      SchemaInfo(info.document, info.isOptional)
    }

    override def refine[A, B](schema: Schema[A], refinement: smithy4s.Refinement[A, B]): SchemaInfo[B] = {
      val info = schema.compile(this)
      SchemaInfo(info.document, info.isOptional)
    }

    override def lazily[A](suspend: smithy4s.Lazy[Schema[A]]): SchemaInfo[A] =
      suspend.value.compile(this)

    override def option[A](schema: Schema[A]): SchemaInfo[Option[A]] = {
      val innerInfo = schema.compile(this)
      SchemaInfo(innerInfo.document, isOptional = true)
    }
  }

  private def applyConstraints(document: Document, hints: Hints): Document = {
    val constraints = scala.collection.mutable.Map[String, Document]()

    hints.get[Pattern].foreach { pattern =>
      constraints("pattern") = Document.fromString(pattern.value)
    }

    hints.get[Length].foreach { length =>
      length.min.foreach(min => constraints("minLength") = Document.fromString(min.toString))
      length.max.foreach(max => constraints("maxLength") = Document.fromString(max.toString))
    }

    hints.get[Range].foreach { range =>
      range.min.foreach(min => constraints("minimum") = Document.fromString(min.toString))
      range.max.foreach(max => constraints("maximum") = Document.fromString(max.toString))
    }

    hints.get[Documentation].foreach { doc =>
      constraints("description") = Document.fromString(doc.value)
    }

    if (constraints.nonEmpty) merge(document, Document.obj(constraints.toSeq*))
    else document
  }
}
