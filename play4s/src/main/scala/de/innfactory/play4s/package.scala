package de.innfactory

import cats.data.{EitherT, Kleisli}
import org.slf4j
import play.api.Logger
import play.api.mvc.RequestHeader
import smithy4s.http.{CaseInsensitive, HttpEndpoint, PathSegment, matchPath}

import scala.concurrent.Future

package object play4s {

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
    /*val trimmedEp = new HttpEndpoint[I] {
    override def path(input: I): List[String] = ep.path(input: I)

    override def path: List[PathSegment] = ep.path.map {
      case PathSegment.StaticSegment(value) =>
        PathSegment.StaticSegment(value.trim)
      case PathSegment.LabelSegment(value) =>
        PathSegment.LabelSegment(value.trim)
      case PathSegment.GreedySegment(value) =>
        PathSegment.GreedySegment(value.trim)
    }

    override def method: HttpMethod = ep.method

    override def code: Int = ep.code
  }
  trimmedEp
    .matches(x.path.replaceFirst("/", "").split("/"))
    .isDefined */
    val trimmedPath = ep.path.map {
      case PathSegment.StaticSegment(value) =>
        PathSegment.StaticSegment(value.trim)
      case PathSegment.LabelSegment(value) =>
        PathSegment.LabelSegment(value.trim)
      case PathSegment.GreedySegment(value) =>
        PathSegment.GreedySegment(value.trim)
    }
    matchPath(trimmedPath, x.path.replaceFirst("/", "").split("/"))
  }

}
