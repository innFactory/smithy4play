package de.innfactory.smithy4play.protocol

import smithy4s.schema.Schema._

case class Untagged()
object Untagged extends smithy4s.ShapeTag.Companion[Untagged] {
  val id: smithy4s.ShapeId = smithy4s.ShapeId("de.innfactory.smithy4play.protocol", "untagged")

  val hints : smithy4s.Hints = smithy4s.Hints(
    smithy.api.Trait(Some("union :not([trait|smithy4s.api#discriminated])"), None, None),
  )

  implicit val schema: smithy4s.Schema[Untagged] = constant(Untagged()).withId(id).addHints(hints)
}