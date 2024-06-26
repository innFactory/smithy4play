package testDefinitions.test

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

case class TestRequestBody(message: String)
object TestRequestBody extends ShapeTag.Companion[TestRequestBody] {
  val id: ShapeId = ShapeId("testDefinitions.test", "TestRequestBody")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[TestRequestBody] = struct(
    string.required[TestRequestBody]("message", _.message).addHints(smithy.api.Required())
  ) {
    TestRequestBody.apply
  }.withId(id).addHints(hints)
}
