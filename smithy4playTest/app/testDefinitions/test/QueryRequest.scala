package testDefinitions.test

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

case class QueryRequest(testQuery: String)
object QueryRequest extends ShapeTag.Companion[QueryRequest] {
  val id: ShapeId = ShapeId("testDefinitions.test", "QueryRequest")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[QueryRequest] = struct(
    string
      .required[QueryRequest]("testQuery", _.testQuery)
      .addHints(smithy.api.Required(), smithy.api.HttpQuery("testQuery"))
  ) {
    QueryRequest.apply
  }.withId(id).addHints(hints)
}
