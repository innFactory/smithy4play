package main.scala.smithy4s

import cats.data.{EitherT, Kleisli}
import org.slf4j
import play.api.Logger
import play.api.mvc.RequestHeader
import smithy4s.http.CaseInsensitive

import scala.concurrent.Future

package object play4s {


  type RouteResult[O] = EitherT[Future, ContextRouteError, O]

  type ContextRoute[O] = Kleisli[RouteResult, RoutingContext, O]

  val logger: slf4j.Logger = Logger("play4s").logger

  def getHeaders(req: RequestHeader) =
    req.headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

}
