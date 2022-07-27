package protocol

import smithy4s.Document
import smithy4s.schema.Schema._

case class UncheckedExample(title: String, documentation: Option[String] = None, input: Option[Document] = None, output: Option[Document] = None)
object UncheckedExample extends smithy4s.ShapeTag.Companion[UncheckedExample] {
  val id: smithy4s.ShapeId = smithy4s.ShapeId("protocol", "UncheckedExample")

  val hints : smithy4s.Hints = smithy4s.Hints(
    smithy.api.Private(),
  )

  implicit val schema: smithy4s.Schema[UncheckedExample] = struct(
    string.required[UncheckedExample]("title", _.title).addHints(smithy.api.Required()),
    string.optional[UncheckedExample]("documentation", _.documentation),
    document.optional[UncheckedExample]("input", _.input),
    document.optional[UncheckedExample]("output", _.output),
  ){
    UncheckedExample.apply
  }.withId(id).addHints(hints)
}