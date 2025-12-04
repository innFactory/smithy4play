package de.innfactory.smithy4play.mcp.server.util

import smithy.api.{ Default, HttpLabel, HttpPayload, HttpQuery }
import smithy4s.schema.{ Alt, Field, Primitive, SchemaVisitor }
import smithy4s.{ Document, Hints, Schema, ShapeId }

import scala.collection.mutable

object InputSchemaBuilder {

  private case class SchemaInfo[A](document: Document, isOptional: Boolean)

  def build[A](schema: Schema[A]): Document = {
    val schemaInfo = schema.compile(SchemaToJsonSchemaVisitor)
    schemaInfo.document
  }

  private object SchemaToJsonSchemaVisitor extends SchemaVisitor[SchemaInfo] { self =>

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

      val result = hints.get[Default] match {
        case Some(default) => merge(withFormat, Document.obj("default" -> default.value))
        case None          => withFormat
      }

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

        val withDescription = merge(
          withLocation,
          Document.obj("description" -> Document.fromString(s"Field ${field.label}"))
        )

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

    override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, ?]],
      dispatch: Alt.Dispatcher[U]
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
            s"Discriminated union for ${shapeId.name}. " +
              s"Must be an object with exactly ONE key from: ${alternatives.map(_.label).mkString(", ")}. " +
              s"The key determines which variant is used, and its value must match that variant's schema."
          ),
          "oneOf"           -> Document.array(variants*),
          "x-discriminator" -> Document.fromString("The object key determines the variant type"),
          "x-usage"         -> Document.fromString(
            "To create this union, pass an object with a single key matching one of the variant names. " +
              "For example: {\"basic\": {...fields...}} or {\"appointment\": {...fields...}}"
          )
        ),
        isOptional = false
      )
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

  private def merge(a: Document, b: Document): Document = (a, b) match {
    case (Document.DObject(f1), Document.DObject(f2)) => Document.obj((f1 ++ f2).toSeq*)
    case _                                            => b
  }
}
