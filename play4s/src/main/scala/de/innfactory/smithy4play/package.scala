package de.innfactory

import cats.data.{EitherT, Kleisli}
import org.slf4j
import play.api.Logger
import play.api.mvc.RequestHeader
import smithy4s.http.{CaseInsensitive, HttpEndpoint, PathSegment, matchPath}

import scala.concurrent.Future

package object smithy4play {

  type RouteResult[O] = EitherT[Future, ContextRouteError, O]

  type ContextRoute[O] = Kleisli[RouteResult, RoutingContext, O]

  val logger: slf4j.Logger = Logger("play4s").logger

  def getHeaders(req: RequestHeader) =
    req.headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  def matchRequestPath(
      x: RequestHeader,
      ep: HttpEndpoint[_]
  ): Option[Map[String, String]] = {
    ep.path.map({
      case PathSegment.StaticSegment(value) =>
        if (value.contains(" "))
          logger.info("following pathSegment contains a space: " + value)
        PathSegment.StaticSegment(value)
      case PathSegment.LabelSegment(value) =>
        if (value.contains(" "))
          logger.info("following pathSegment contains a space: " + value)
        PathSegment.LabelSegment(value)
      case PathSegment.GreedySegment(value) =>
        if (value.contains(" "))
          logger.info("following pathSegment contains a space: " + value)
        PathSegment.GreedySegment(value)
    })
    ep.matches(x.path.replaceFirst("/", "").split("/"))
  }

}
