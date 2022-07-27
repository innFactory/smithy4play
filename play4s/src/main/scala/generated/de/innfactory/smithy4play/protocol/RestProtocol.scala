package de.innfactory.smithy4play.protocol

import smithy4s.schema.Schema._

case class RestProtocol()
object RestProtocol extends smithy4s.ShapeTag.Companion[RestProtocol] {
  val id: smithy4s.ShapeId = smithy4s.ShapeId("de.innfactory.smithy4play.protocol", "restProtocol")

  val hints : smithy4s.Hints = smithy4s.Hints(
    smithy.api.ProtocolDefinition(Some(List(smithy.api.TraitShapeId("smithy.api#error"), smithy.api.TraitShapeId("smithy.api#required"), smithy.api.TraitShapeId("smithy.api#pattern"), smithy.api.TraitShapeId("smithy.api#range"), smithy.api.TraitShapeId("smithy.api#length"), smithy.api.TraitShapeId("smithy.api#http"), smithy.api.TraitShapeId("smithy.api#httpError"), smithy.api.TraitShapeId("smithy.api#httpHeader"), smithy.api.TraitShapeId("smithy.api#httpLabel"), smithy.api.TraitShapeId("smithy.api#httpPayload"), smithy.api.TraitShapeId("smithy.api#httpPrefixHeaders"), smithy.api.TraitShapeId("smithy.api#httpQuery"), smithy.api.TraitShapeId("smithy.api#httpQueryParams"), smithy.api.TraitShapeId("smithy.api#mediaType"), smithy.api.TraitShapeId("smithy.api#jsonName"), smithy.api.TraitShapeId("smithy.api#timestampFormat"), smithy.api.TraitShapeId("de.innfactory.smithy4play.protocol#uncheckedExamples"), smithy.api.TraitShapeId("de.innfactory.smithy4play.protocol#uuidFormat"), smithy.api.TraitShapeId("de.innfactory.smithy4play.protocol#discriminated"), smithy.api.TraitShapeId("de.innfactory.smithy4play.protocol#untagged"))), None),
    smithy.api.Trait(Some("service"), None, None),
  )

  implicit val protocol: smithy4s.Protocol[RestProtocol] = new smithy4s.Protocol[RestProtocol] {
    def traits: Set[smithy4s.ShapeId] = Set(smithy4s.ShapeId("smithy.api", "error"), smithy4s.ShapeId("smithy.api", "required"), smithy4s.ShapeId("smithy.api", "pattern"), smithy4s.ShapeId("smithy.api", "range"), smithy4s.ShapeId("smithy.api", "length"), smithy4s.ShapeId("smithy.api", "http"), smithy4s.ShapeId("smithy.api", "httpError"), smithy4s.ShapeId("smithy.api", "httpHeader"), smithy4s.ShapeId("smithy.api", "httpLabel"), smithy4s.ShapeId("smithy.api", "httpPayload"), smithy4s.ShapeId("smithy.api", "httpPrefixHeaders"), smithy4s.ShapeId("smithy.api", "httpQuery"), smithy4s.ShapeId("smithy.api", "httpQueryParams"), smithy4s.ShapeId("smithy.api", "mediaType"), smithy4s.ShapeId("smithy.api", "jsonName"), smithy4s.ShapeId("smithy.api", "timestampFormat"), smithy4s.ShapeId("de.innfactory.smithy4play.protocol", "uncheckedExamples"), smithy4s.ShapeId("de.innfactory.smithy4play.protocol", "uuidFormat"), smithy4s.ShapeId("de.innfactory.smithy4play.protocol", "discriminated"), smithy4s.ShapeId("de.innfactory.smithy4play.protocol", "untagged"))
  }

  implicit val schema: smithy4s.Schema[RestProtocol] = constant(RestProtocol()).withId(id).addHints(hints)
}