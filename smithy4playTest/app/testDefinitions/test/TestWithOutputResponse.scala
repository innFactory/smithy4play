package testDefinitions.test

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.struct

case class TestWithOutputResponse(body: TestResponseBody)
object TestWithOutputResponse extends ShapeTag.Companion[TestWithOutputResponse] {
  val id: ShapeId = ShapeId("testDefinitions.test", "TestWithOutputResponse")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[TestWithOutputResponse] = struct(
    TestResponseBody.schema
      .required[TestWithOutputResponse]("body", _.body)
      .addHints(smithy.api.HttpPayload(), smithy.api.Required())
  ) {
    TestWithOutputResponse.apply
  }.withId(id).addHints(hints)
}
