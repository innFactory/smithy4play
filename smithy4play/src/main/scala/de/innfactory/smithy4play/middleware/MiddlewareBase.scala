package de.innfactory.smithy4play.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.{ EndpointRequest, RouteResult, RoutingContext }
import play.api.Logger
import smithy4s.Blob

trait MiddlewareBase {

  val logger: Logger = Logger("smithy4play")

  protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointRequest]
  ): RouteResult[EndpointRequest]

  protected def skipMiddleware(r: RoutingContext): Boolean = false

  def middleware(
    f: RoutingContext => RouteResult[EndpointRequest]
  ): Kleisli[RouteResult, RoutingContext, EndpointRequest] =
    Kleisli { r =>
      if (skipMiddleware(r)) f(r)
      else logic(r, f)
    }

}
