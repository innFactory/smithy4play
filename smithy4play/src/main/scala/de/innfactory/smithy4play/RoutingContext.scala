package de.innfactory.smithy4play

import play.api.mvc.{ RawBuffer, Request }
import smithy4s.Hints

case class RoutingContext(
  headers: Map[String, Seq[String]],
  serviceHints: Seq[Hints],
  endpointHints: Seq[Hints],
  attributes: Map[String, Any]
)

object RoutingContext {
  def fromRequest(request: Request[RawBuffer]): RoutingContext =
    RoutingContext(request.headers.toMap, Seq.empty, Seq.empty, Map.empty)
}
