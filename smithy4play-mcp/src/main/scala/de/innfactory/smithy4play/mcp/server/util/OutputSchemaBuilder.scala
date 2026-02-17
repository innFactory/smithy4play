package de.innfactory.smithy4play.mcp.server.util

import smithy4s.schema.{ Alt, Field, Primitive, SchemaVisitor }
import smithy4s.{ Document, Hints, Schema, ShapeId }
import de.innfactory.smithy4play.mcp.server.util.SchemaUtils.primitiveToJsonType

object OutputSchemaBuilder {

  private case class SchemaInfo[A](document: Document)

  def build[A](schema: Schema[A], recursive: Boolean = false): Option[Document] = {
    val compiled = schema.compile(OutputSchemaVisitor(recursive)).document
    compiled match {
      // Empty struct (no fields / Schema.unit) → no output schema
      case Document.DObject(fields)
          if fields.get("type").contains(Document.fromString("object")) &&
            fields.get("properties").contains(Document.obj()) =>
        None
      // Already type: object with properties → pass through
      case Document.DObject(fields) if fields.get("type").contains(Document.fromString("object")) =>
        Some(compiled)
      // Union (oneOf without type) → add type: object
      case Document.DObject(fields) if fields.contains("oneOf") && !fields.contains("type")       =>
        Some(Document.DObject(fields + ("type" -> Document.fromString("object"))))
      // Non-object schema (array, primitive, enum, etc.) → wrap in result property
      case other                                                                                  =>
        Some(
          Document.obj(
            "type"       -> Document.fromString("object"),
            "properties" -> Document.obj("result" -> other)
          )
        )
    }
  }

  private class OutputSchemaVisitor(recursive: Boolean) extends SchemaVisitor[SchemaInfo] {

    private def fieldVisitor: SchemaVisitor[SchemaInfo] =
      if (recursive) this else ShallowTypeVisitor

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): SchemaInfo[P] =
      SchemaInfo(Document.obj("type" -> Document.fromString(primitiveToJsonType(tag))))

    override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: smithy4s.schema.CollectionTag[C],
      member: Schema[A]
    ): SchemaInfo[C[A]] = {
      val memberInfo = member.compile(fieldVisitor)
      SchemaInfo(
        Document.obj(
          "type"  -> Document.fromString("array"),
          "items" -> memberInfo.document
        )
      )
    }

    override def map[K, V](shapeId: ShapeId, hints: Hints, key: Schema[K], value: Schema[V]): SchemaInfo[Map[K, V]] = {
      val valueInfo = value.compile(fieldVisitor)
      SchemaInfo(
        Document.obj(
          "type"                 -> Document.fromString("object"),
          "additionalProperties" -> valueInfo.document
        )
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
          "type"  -> Document.fromString("string"),
          "oneOf" -> Document.array(
            values.map(v =>
              Document.obj(
                "const" -> Document.fromString(v.stringValue),
                "title" -> Document.fromString(v.name)
              )
            )*
          )
        )
      )

    override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, ?]],
      make: IndexedSeq[Any] => S
    ): SchemaInfo[S] = {
      // Check if any field has @httpPayload
      val payloadField = fields.find(_.hints.get(using smithy.api.HttpPayload).isDefined)

      payloadField match {
        case Some(field) =>
          // Unwrap: compile the payload field's schema with the main visitor
          // to resolve its structure (not fieldVisitor, which may be too shallow)
          SchemaInfo(field.schema.compile(this).document)
        case None        =>
          // Filter out HTTP-binding fields that don't appear in the JSON body
          val bodyFields = fields.filter { field =>
            val h = field.hints
            h.get(using smithy.api.HttpHeader).isEmpty &&
            h.get(using smithy.api.HttpQuery).isEmpty &&
            h.get(using smithy.api.HttpLabel).isEmpty &&
            h.get(using smithy.api.HttpResponseCode).isEmpty &&
            h.get(using smithy.api.HttpPrefixHeaders).isEmpty
          }

          val properties = bodyFields.map { field =>
            field.label -> field.schema.compile(fieldVisitor).document
          }

          SchemaInfo(
            Document.obj(
              "type"       -> Document.fromString("object"),
              "properties" -> Document.obj(properties*)
            )
          )
      }
    }

    override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, ?]],
      dispatch: Alt.Dispatcher[U]
    ): SchemaInfo[U] = {
      val variants = alternatives.map { alt =>
        Document.obj(
          "type"       -> Document.fromString("object"),
          "properties" -> Document.obj(alt.label -> alt.schema.compile(fieldVisitor).document)
        )
      }
      SchemaInfo(Document.obj("oneOf" -> Document.array(variants*)))
    }

    override def biject[A, B](schema: Schema[A], bijection: smithy4s.Bijection[A, B]): SchemaInfo[B] =
      SchemaInfo(schema.compile(this).document)

    override def refine[A, B](schema: Schema[A], refinement: smithy4s.Refinement[A, B]): SchemaInfo[B] =
      SchemaInfo(schema.compile(this).document)

    override def lazily[A](suspend: smithy4s.Lazy[Schema[A]]): SchemaInfo[A] =
      suspend.value.compile(this)

    override def option[A](schema: Schema[A]): SchemaInfo[Option[A]] =
      SchemaInfo(schema.compile(this).document)
  }

  /** Visitor that only returns the top-level JSON Schema type without descending into children. */
  private object ShallowTypeVisitor extends SchemaVisitor[SchemaInfo] {

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): SchemaInfo[P] =
      SchemaInfo(Document.obj("type" -> Document.fromString(primitiveToJsonType(tag))))

    override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: smithy4s.schema.CollectionTag[C],
      member: Schema[A]
    ): SchemaInfo[C[A]] =
      SchemaInfo(Document.obj("type" -> Document.fromString("array")))

    override def map[K, V](shapeId: ShapeId, hints: Hints, key: Schema[K], value: Schema[V]): SchemaInfo[Map[K, V]] =
      SchemaInfo(Document.obj("type" -> Document.fromString("object")))

    override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: smithy4s.schema.EnumTag[E],
      values: List[smithy4s.schema.EnumValue[E]],
      total: E => smithy4s.schema.EnumValue[E]
    ): SchemaInfo[E] =
      SchemaInfo(Document.obj("type" -> Document.fromString("string")))

    override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, ?]],
      make: IndexedSeq[Any] => S
    ): SchemaInfo[S] =
      SchemaInfo(Document.obj("type" -> Document.fromString("object")))

    override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, ?]],
      dispatch: Alt.Dispatcher[U]
    ): SchemaInfo[U] =
      SchemaInfo(Document.obj("type" -> Document.fromString("object")))

    override def biject[A, B](schema: Schema[A], bijection: smithy4s.Bijection[A, B]): SchemaInfo[B] =
      SchemaInfo(schema.compile(this).document)

    override def refine[A, B](schema: Schema[A], refinement: smithy4s.Refinement[A, B]): SchemaInfo[B] =
      SchemaInfo(schema.compile(this).document)

    override def lazily[A](suspend: smithy4s.Lazy[Schema[A]]): SchemaInfo[A] =
      suspend.value.compile(this)

    override def option[A](schema: Schema[A]): SchemaInfo[Option[A]] =
      SchemaInfo(schema.compile(this).document)
  }
}
