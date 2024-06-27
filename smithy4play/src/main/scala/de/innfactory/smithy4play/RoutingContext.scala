package de.innfactory.smithy4play

import play.api.mvc.{ RawBuffer, Request, RequestHeader }
import smithy4s.{ Hints, ShapeTag }

trait RoutingContext {
  def headers: Map[String, Seq[String]]

  def serviceHints: Hints

  def attributes: Map[String, Any]

  def requestHeader: RequestHeader

  val rawBody: Request[RawBuffer]
  def hasHints(s: ShapeTag.Companion[?]): Boolean        = hasServiceHints(s)
  def hasServiceHints(s: ShapeTag.Companion[?]): Boolean = serviceHints.has(s.tagInstance)
}

object RoutingContext {
  def fromRequest(
    request: Request[RawBuffer],
    sHints: Hints,
    requestHeader: RequestHeader
  ): RoutingContext =
    RoutingContextWithoutEndpointHints(request.headers.toMap, sHints, Map.empty, requestHeader, request)
}

case class RoutingContextWithoutEndpointHints(
  headers: Map[String, Seq[String]],
  serviceHints: Hints,
  attributes: Map[String, Any],
  requestHeader: RequestHeader,
  rawBody: Request[RawBuffer]
) extends RoutingContext

object RoutingContextWithoutEndpointHints {
  def fromRequest(
    request: Request[RawBuffer],
    sHints: Hints,
    eHints: Hints,
    requestHeader: RequestHeader
  ): RoutingContext =
    RoutingContextWithoutEndpointHints(request.headers.toMap, sHints, Map.empty, requestHeader, request)
}

case class RoutingContextWithEndpointHints(
  headers: Map[String, Seq[String]],
  serviceHints: Hints,
  endpointHints: Hints,
  attributes: Map[String, Any],
  requestHeader: RequestHeader,
  rawBody: Request[RawBuffer]
) extends RoutingContext {
  override def hasHints(s: ShapeTag.Companion[?]): Boolean = hasEndpointHints(s) || hasServiceHints(s)
  def hasEndpointHints(s: ShapeTag.Companion[?]): Boolean  = endpointHints.has(s.tagInstance)
}

object RoutingContextWithEndpointHints {
  def fromRCWithoutEndpointHints(
    rc: RoutingContextWithoutEndpointHints,
    eHints: Hints
  ): RoutingContext =
    RoutingContextWithEndpointHints(rc.headers, rc.serviceHints, eHints, rc.attributes, rc.requestHeader, rc.rawBody)
}
