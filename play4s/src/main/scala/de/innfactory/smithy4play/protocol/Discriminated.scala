package protocol

import smithy4s.Newtype
import smithy4s.schema.Schema._

object Discriminated extends Newtype[String] {
  val id: smithy4s.ShapeId = smithy4s.ShapeId("protocol", "discriminated")
  val hints : smithy4s.Hints = smithy4s.Hints(
    smithy.api.Trait(Some("union :not([trait|smithy4s.api#untagged])"), None, None),
  )
  val underlyingSchema : smithy4s.Schema[String] = string.withId(id).addHints(hints)
  implicit val schema : smithy4s.Schema[Discriminated] = bijection(underlyingSchema, Discriminated.make, (_ : Discriminated).value)
}