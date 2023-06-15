package de.innfactory.smithy4play

import cats.data.{ EitherT, Kleisli }
import play.api.Logger
import smithy4s.ShapeTag

import scala.concurrent.{ ExecutionContext, Future }

trait MiddlewareBase {

  val logger: Logger = Logger("smithy4play")

  protected def logic(r: RoutingContext): RouteResult[RoutingContext]

  protected def skipMiddleware(r: RoutingContext): Boolean = false

  def middleware(implicit executionContext: ExecutionContext): Kleisli[RouteResult, RoutingContext, RoutingContext] =
    Kleisli { r =>
      if (skipMiddleware(r)) EitherT.rightT[Future, ContextRouteError](r)
      else logic(r)
    }

}
