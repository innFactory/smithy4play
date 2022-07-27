package protocol

import smithy4s.Newtype
import smithy4s.schema.Schema._

object UncheckedExamples extends Newtype[List[UncheckedExample]] {
  val id: smithy4s.ShapeId = smithy4s.ShapeId("protocol", "uncheckedExamples")
  val hints : smithy4s.Hints = smithy4s.Hints(
    smithy.api.Documentation("A version of @examples that is not tied to a validator"),
    smithy.api.Trait(Some("operation"), None, None),
  )
  val underlyingSchema : smithy4s.Schema[List[UncheckedExample]] = list(UncheckedExample.schema).withId(id).addHints(hints)
  implicit val schema : smithy4s.Schema[UncheckedExamples] = bijection(underlyingSchema, UncheckedExamples.make, (_ : UncheckedExamples).value)
}