package de.innfactory.smithy4play.routing.context

import play.api.mvc.{ RawBuffer, Request, RequestHeader }
import smithy4s.{ Hints, ShapeTag }

trait RoutingContextBase {
  def headers: Map[String, Seq[String]]
  def serviceHints: Hints
  def attributes: Map[String, Any]
  def requestHeader: RequestHeader
  val rawBody: Request[RawBuffer]
  def hasHints(s: ShapeTag.Companion[?]): Boolean        = hasServiceHints(s)
  def hasServiceHints(s: ShapeTag.Companion[?]): Boolean = serviceHints.has(using s.tagInstance)
}

object RoutingContextBase {
  def fromRequest(
    request: Request[RawBuffer],
    sHints: Hints,
    requestHeader: RequestHeader
  ): RoutingContextBase =
    RoutingContextWithoutEndpointHints(request.headers.toMap, sHints, Map.empty, requestHeader, request)
}
