package de.innfactory.smithy4play.routing.context

import play.api.mvc.{ RawBuffer, Request, RequestHeader }
import smithy4s.{ Hints, ShapeTag }

case class RoutingContextWithoutEndpointHints(
  headers: Map[String, Seq[String]],
  serviceHints: Hints,
  attributes: Map[String, Any],
  requestHeader: RequestHeader,
  rawBody: Request[RawBuffer]
) extends RoutingContextBase

object RoutingContextWithoutEndpointHints {
  def fromRequest(
    request: Request[RawBuffer],
    sHints: Hints,
    eHints: Hints,
    requestHeader: RequestHeader
  ): RoutingContextBase =
    RoutingContextWithoutEndpointHints(request.headers.toMap, sHints, Map.empty, requestHeader, request)
}
