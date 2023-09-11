package de.innfactory.smithy4play

import play.api.mvc.{ RawBuffer, Request, RequestHeader }
import smithy4s.{ Hints, ShapeTag }

case class RoutingContext(
  headers: Map[String, Seq[String]],
  serviceHints: Hints,
  endpointHints: Hints,
  attributes: Map[String, Any],
  requestHeader: RequestHeader
) {
  def hasHints(s: ShapeTag.Companion[_]): Boolean         = hasEndpointHints(s) || hasServiceHints(s)
  def hasServiceHints(s: ShapeTag.Companion[_]): Boolean  = serviceHints.has(s.tagInstance)
  def hasEndpointHints(s: ShapeTag.Companion[_]): Boolean = endpointHints.has(s.tagInstance)
}

object RoutingContext {
  def fromRequest(
    request: Request[RawBuffer],
    sHints: Hints,
    eHints: Hints,
    requestHeader: RequestHeader
  ): RoutingContext =
    RoutingContext(request.headers.toMap, sHints, eHints, Map.empty, requestHeader)
}
