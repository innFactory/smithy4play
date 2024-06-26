package testDefinitions.test

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

case class SimpleTestResponse(message: Option[String] = None)
object SimpleTestResponse extends ShapeTag.Companion[SimpleTestResponse] {
  val id: ShapeId = ShapeId("testDefinitions.test", "SimpleTestResponse")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[SimpleTestResponse] = struct(
    string.optional[SimpleTestResponse]("message", _.message)
  ) {
    SimpleTestResponse.apply
  }.withId(id).addHints(hints)
}
