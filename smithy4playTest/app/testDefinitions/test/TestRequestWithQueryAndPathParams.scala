package testDefinitions.test

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

case class TestRequestWithQueryAndPathParams(
  pathParam: String,
  testQuery: String,
  testHeader: String,
  body: TestRequestBody
)
object TestRequestWithQueryAndPathParams extends ShapeTag.Companion[TestRequestWithQueryAndPathParams] {
  val id: ShapeId = ShapeId("testDefinitions.test", "TestRequestWithQueryAndPathParams")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[TestRequestWithQueryAndPathParams] = struct(
    string
      .required[TestRequestWithQueryAndPathParams]("pathParam", _.pathParam)
      .addHints(smithy.api.HttpLabel(), smithy.api.Required()),
    string
      .required[TestRequestWithQueryAndPathParams]("testQuery", _.testQuery)
      .addHints(smithy.api.Required(), smithy.api.HttpQuery("testQuery")),
    string
      .required[TestRequestWithQueryAndPathParams]("testHeader", _.testHeader)
      .addHints(smithy.api.HttpHeader("Test-Header"), smithy.api.Required()),
    TestRequestBody.schema
      .required[TestRequestWithQueryAndPathParams]("body", _.body)
      .addHints(smithy.api.HttpPayload(), smithy.api.Required())
  ) {
    TestRequestWithQueryAndPathParams.apply
  }.withId(id).addHints(hints)
}
