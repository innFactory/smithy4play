package de.innfactory.smithy4play.routing.context

import play.api.mvc.{ RawBuffer, Request, RequestHeader }
import smithy4s.{ Hints, ShapeTag }

case class RoutingContext(
  headers: Map[String, Seq[String]],
  serviceHints: Hints,
  endpointHints: Hints,
  attributes: Map[String, Any],
  requestHeader: RequestHeader,
  rawBody: Request[RawBuffer]
) extends RoutingContextBase {
  override def hasHints(s: ShapeTag.Companion[?]): Boolean = hasEndpointHints(s) || hasServiceHints(s)
  def hasEndpointHints(s: ShapeTag.Companion[?]): Boolean  = endpointHints.has(using s.tagInstance)
}

object RoutingContext {
  def fromRCWithoutEndpointHints(
    rc: RoutingContextWithoutEndpointHints,
    eHints: Hints
  ): RoutingContextBase =
    RoutingContext(rc.headers, rc.serviceHints, eHints, rc.attributes, rc.requestHeader, rc.rawBody)
}
