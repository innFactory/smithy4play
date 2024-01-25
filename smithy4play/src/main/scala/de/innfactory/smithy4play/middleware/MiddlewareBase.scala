package de.innfactory.smithy4play.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.{EndpointResult, RouteResult, RoutingContext}
import play.api.Logger

trait MiddlewareBase {

  val logger: Logger = Logger("smithy4play")

  protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointResult]
  ): RouteResult[EndpointResult]

  protected def skipMiddleware(r: RoutingContext): Boolean = false

  def middleware(
    f: RoutingContext => RouteResult[EndpointResult]
  ): Kleisli[RouteResult, RoutingContext, EndpointResult] =
    Kleisli { r =>
      if (skipMiddleware(r)) f(r)
      else logic(r, f)
    }

}
