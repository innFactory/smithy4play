package de.innfactory.smithy4play.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.{ RouteResult, RoutingContext }
import play.api.Logger
import smithy4s.Blob
import smithy4s.http.HttpResponse

trait MiddlewareBase {

  val logger: Logger = Logger("smithy4play")

  protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[HttpResponse[Blob]]
  ): RouteResult[HttpResponse[Blob]]

  protected def skipMiddleware(r: RoutingContext): Boolean = false

  def middleware(
    f: RoutingContext => RouteResult[HttpResponse[Blob]]
  ): Kleisli[RouteResult, RoutingContext, HttpResponse[Blob]] =
    Kleisli { r =>
      if (skipMiddleware(r)) f(r)
      else logic(r, f)
    }

}
