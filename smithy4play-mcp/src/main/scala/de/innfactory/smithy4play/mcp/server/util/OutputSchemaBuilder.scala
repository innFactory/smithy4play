package de.innfactory.smithy4play.mcp.server.util

import smithy4s.schema.{ Alt, Field, Primitive, SchemaVisitor }
import smithy4s.{ Document, Hints, Schema, ShapeId }

object OutputSchemaBuilder {

  private case class FieldNames[A](names: Vector[String])

  def build[A](schema: Schema[A]): Document = {
    val fieldNames = schema.compile(FieldNameExtractor)
    Document.array(fieldNames.names.map(Document.fromString)*)
  }

  private object FieldNameExtractor extends SchemaVisitor[FieldNames] {

    override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): FieldNames[P] =
      FieldNames(Vector.empty)

    override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: smithy4s.schema.CollectionTag[C],
      member: Schema[A]
    ): FieldNames[C[A]] = {
      val memberFields = member.compile(this)
      if (memberFields.names.nonEmpty) FieldNames(memberFields.names.map(f => s"[].$f"))
      else FieldNames(Vector("[]"))
    }

    override def map[K, V](shapeId: ShapeId, hints: Hints, key: Schema[K], value: Schema[V]): FieldNames[Map[K, V]] = {
      val valueFields = value.compile(this)
      if (valueFields.names.nonEmpty) FieldNames(valueFields.names.map(f => s"{}.$f"))
      else FieldNames(Vector("{}"))
    }

    override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: smithy4s.schema.EnumTag[E],
      values: List[smithy4s.schema.EnumValue[E]],
      total: E => smithy4s.schema.EnumValue[E]
    ): FieldNames[E] = FieldNames(Vector.empty)

    override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, ?]],
      make: IndexedSeq[Any] => S
    ): FieldNames[S] = {
      val names = fields.flatMap { field =>
        val nested = field.schema.compile(this)
        if (nested.names.isEmpty) Vector(field.label)
        else nested.names.map(n => s"${field.label}.$n")
      }
      FieldNames(names)
    }

    override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, ?]],
      dispatch: Alt.Dispatcher[U]
    ): FieldNames[U] = {
      val names = alternatives.flatMap { alt =>
        val nested = alt.schema.compile(this)
        if (nested.names.isEmpty) Vector(alt.label)
        else nested.names.map(n => s"${alt.label}.$n")
      }
      FieldNames(names)
    }

    override def biject[A, B](schema: Schema[A], bijection: smithy4s.Bijection[A, B]): FieldNames[B] =
      FieldNames(schema.compile(this).names)

    override def refine[A, B](schema: Schema[A], refinement: smithy4s.Refinement[A, B]): FieldNames[B] =
      FieldNames(schema.compile(this).names)

    override def lazily[A](suspend: smithy4s.Lazy[Schema[A]]): FieldNames[A] =
      suspend.value.compile(this)

    override def option[A](schema: Schema[A]): FieldNames[Option[A]] =
      FieldNames(schema.compile(this).names)
  }
}
