package de.innfactory.smithy4play.mcp.server.util

import smithy4s.schema.{ Alt, Field, Primitive, SchemaVisitor }
import smithy4s.{ Document, Hints, Schema, ShapeId }

object OutputSchemaBuilder {

  private case class SchemaInfo[A](document: Document)

  def build[A](schema: Schema[A], recursive: Boolean = false): Document = {
    val compiled = schema.compile(OutputSchemaVisitor(recursive)).document
    compiled match {
      case Document.DObject(fields) if fields.get("type").contains(Document.fromString("object")) => compiled
      case other                                                                                  =>
        Document.obj(
          "type"       -> Document.fromString("object"),
          "properties" -> Document.obj("result" -> other)
        )
    }
  }

  private class OutputSchemaVisitor(recursive: Boolean) extends SchemaVisitor[SchemaInfo] {

    private val shallowVisitor: SchemaVisitor[SchemaInfo] = ShallowTypeVisitor

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): SchemaInfo[P] = {
      val baseType = tag match {
        case Primitive.PInt | Primitive.PShort | Primitive.PLong | Primitive.PByte            => "integer"
        case Primitive.PFloat | Primitive.PDouble | Primitive.PBigDecimal | Primitive.PBigInt => "number"
        case Primitive.PBoolean                                                               => "boolean"
        case Primitive.PString | Primitive.PUUID | Primitive.PBlob | Primitive.PDocument      => "string"
        case Primitive.PTimestamp                                                             => "string"
      }
      SchemaInfo(Document.obj("type" -> Document.fromString(baseType)))
    }

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
      val properties = fields.map { field =>
        field.label -> field.schema.compile(fieldVisitor).document
      }

      SchemaInfo(
        Document.obj(
          "type"       -> Document.fromString("object"),
          "properties" -> Document.obj(properties*)
        )
      )
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

    private def fieldVisitor: SchemaVisitor[SchemaInfo] =
      if (recursive) this else shallowVisitor
  }

  private object ShallowTypeVisitor extends SchemaVisitor[SchemaInfo] {

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): SchemaInfo[P] = {
      val baseType = tag match {
        case Primitive.PInt | Primitive.PShort | Primitive.PLong | Primitive.PByte            => "integer"
        case Primitive.PFloat | Primitive.PDouble | Primitive.PBigDecimal | Primitive.PBigInt => "number"
        case Primitive.PBoolean                                                               => "boolean"
        case Primitive.PString | Primitive.PUUID | Primitive.PBlob | Primitive.PDocument      => "string"
        case Primitive.PTimestamp                                                             => "string"
      }
      SchemaInfo(Document.obj("type" -> Document.fromString(baseType)))
    }

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
