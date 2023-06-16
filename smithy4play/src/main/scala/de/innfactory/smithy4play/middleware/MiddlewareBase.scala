package de.innfactory.smithy4play.middleware

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.{ ContextRouteError, RouteResult, RoutingContext }
import play.api.Logger
import play.api.mvc.Result

import scala.concurrent.{ ExecutionContext, Future }

trait MiddlewareBase {

  val logger: Logger = Logger("smithy4play")

  protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[Result]
  ): RouteResult[Result]

  protected def skipMiddleware(r: RoutingContext): Boolean = false

  def middleware(
    f: RoutingContext => RouteResult[Result]
  )(implicit executionContext: ExecutionContext): Kleisli[RouteResult, RoutingContext, Result] =
    Kleisli { r =>
      if (skipMiddleware(r)) f(r)
      else logic(r, f)
    }

}
