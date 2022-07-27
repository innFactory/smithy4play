package protocol

import smithy4s.schema.Schema._

case class UuidFormat()
object UuidFormat extends smithy4s.ShapeTag.Companion[UuidFormat] {
  val id: smithy4s.ShapeId = smithy4s.ShapeId("protocol", "uuidFormat")

  val hints : smithy4s.Hints = smithy4s.Hints(
    smithy.api.Trait(Some("string"), None, None),
  )

  implicit val schema: smithy4s.Schema[UuidFormat] = constant(UuidFormat()).withId(id).addHints(hints)
}