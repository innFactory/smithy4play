package testDefinitions.test

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

case class TestResponseBody(testHeader: String, pathParam: String, testQuery: String, bodyMessage: String)
object TestResponseBody extends ShapeTag.Companion[TestResponseBody] {
  val id: ShapeId = ShapeId("testDefinitions.test", "TestResponseBody")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[TestResponseBody] = struct(
    string.required[TestResponseBody]("testHeader", _.testHeader).addHints(smithy.api.Required()),
    string.required[TestResponseBody]("pathParam", _.pathParam).addHints(smithy.api.Required()),
    string.required[TestResponseBody]("testQuery", _.testQuery).addHints(smithy.api.Required()),
    string.required[TestResponseBody]("bodyMessage", _.bodyMessage).addHints(smithy.api.Required())
  ) {
    TestResponseBody.apply
  }.withId(id).addHints(hints)
}
