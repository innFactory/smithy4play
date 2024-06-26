package testDefinitions.test

import smithy4s.ByteArray
import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.bytes
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

case class BlobRequest(body: ByteArray, contentType: String)
object BlobRequest extends ShapeTag.Companion[BlobRequest] {
  val id: ShapeId = ShapeId("testDefinitions.test", "BlobRequest")

  val hints: Hints = Hints.empty

  implicit val schema: Schema[BlobRequest] = struct(
    bytes.required[BlobRequest]("body", _.body).addHints(smithy.api.HttpPayload(), smithy.api.Required()),
    string
      .required[BlobRequest]("contentType", _.contentType)
      .addHints(smithy.api.HttpHeader("cOnTeNt-TyPe"), smithy.api.Required())
  ) {
    BlobRequest.apply
  }.withId(id).addHints(hints)
}
