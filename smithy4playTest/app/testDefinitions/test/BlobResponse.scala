package testDefinitions.test

import smithy4s.ByteArray
import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.bytes
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

case class BlobResponse(body: ByteArray, contentType: String)
object BlobResponse extends ShapeTag.Companion[BlobResponse] {
  val id: ShapeId = ShapeId("testDefinitions.test", "BlobResponse")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[BlobResponse] = struct(
    bytes.required[BlobResponse]("body", _.body).addHints(smithy.api.HttpPayload(), smithy.api.Required()),
    string
      .required[BlobResponse]("contentType", _.contentType)
      .addHints(smithy.api.HttpHeader("cOnTeNt-TyPe"), smithy.api.Required())
  ) {
    BlobResponse.apply
  }.withId(id).addHints(hints)
}
