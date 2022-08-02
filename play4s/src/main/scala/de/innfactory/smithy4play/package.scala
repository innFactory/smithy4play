package de.innfactory

import cats.data.{EitherT, Kleisli}
import org.slf4j
import play.api.Logger
import play.api.mvc.{ControllerComponents, RequestHeader}
import play.api.routing.Router.Routes
import smithy4s.Monadic
import smithy4s.http.{CaseInsensitive, HttpEndpoint, PathSegment, matchPath}

import scala.concurrent.{ExecutionContext, Future}

package object smithy4play {

  type RouteResult[O] = EitherT[Future, ContextRouteError, O]

  type ContextRoute[O] = Kleisli[RouteResult, RoutingContext, O]

  //change name of logger
  val logger: slf4j.Logger = Logger("play4s").logger

  def getHeaders(req: RequestHeader): Map[CaseInsensitive, Seq[String]] =
    req.headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  def matchRequestPath(
      x: RequestHeader,
      ep: HttpEndpoint[_]
  ): Option[Map[String, String]] = {
    ep.matches(x.path.replaceFirst("/", "").split("/").filter(_.nonEmpty))
  }

  trait AutoRoutableController {
    implicit def transformToRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
      _
    ] <: ContextRoute[_]](
                           impl: Monadic[Alg, F]
                         )(implicit serviceProvider: smithy4s.Service.Provider[Alg, Op], ec: ExecutionContext, cc: ControllerComponents): Routes = {
      new SmithyPlayRouter[Alg, Op, F](impl).routes()
    }



    val routes: Routes

  }

}
