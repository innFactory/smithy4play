package de.innfactory.smithy4play

import play.api.mvc.{ RawBuffer, Request }
import smithy4s.Hints

case class RoutingContext(
  headers: Map[String, Seq[String]],
  serviceHints: Hints,
  endpointHints: Hints,
  attributes: Map[String, Any]
)

object RoutingContext {
  def fromRequest(request: Request[RawBuffer], sHints: Hints, eHints: Hints): RoutingContext =
    RoutingContext(request.headers.toMap, sHints, eHints, Map.empty)
}
