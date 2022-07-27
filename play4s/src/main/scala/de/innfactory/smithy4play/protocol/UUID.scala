package protocol

import smithy4s.Newtype
import smithy4s.schema.Schema._

object UUID extends Newtype[String] {
  val id: smithy4s.ShapeId = smithy4s.ShapeId("protocol", "UUID")
  val hints : smithy4s.Hints = smithy4s.Hints(
    protocol.UuidFormat(),
  )
  val underlyingSchema : smithy4s.Schema[String] = string.withId(id).addHints(hints)
  implicit val schema : smithy4s.Schema[UUID] = bijection(underlyingSchema, UUID.make, (_ : UUID).value)
}