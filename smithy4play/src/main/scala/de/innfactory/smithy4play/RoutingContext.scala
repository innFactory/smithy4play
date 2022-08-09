package de.innfactory.smithy4play

import play.api.mvc.{RawBuffer, Request}

case class RoutingContext(map: Map[String, Seq[String]])

object RoutingContext {
  def fromRequest(request: Request[RawBuffer]): RoutingContext =
    RoutingContext(request.headers.toMap)
}
