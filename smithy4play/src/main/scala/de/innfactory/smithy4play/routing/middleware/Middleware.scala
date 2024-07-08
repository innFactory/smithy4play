package de.innfactory.smithy4play.routing.middleware

import de.innfactory.smithy4play.RoutingResult
import de.innfactory.smithy4play.routing.PlayTransformation
import de.innfactory.smithy4play.routing.context.{RoutingContext, RoutingContextBase}
import play.api.mvc.Result
import smithy4s.Endpoint

trait Middleware {

  def logic(
    r: RoutingContext,
    next: RoutingContext => RoutingResult[Result]
  ): RoutingResult[Result] = {
    println("Logic 1")
    next.apply(r)
  }

  private[smithy4play] def resolveMiddleware = middleware.andThen(InjectorMiddleware(logic))

  def middleware: Endpoint.Middleware[PlayTransformation] =
    Endpoint.Middleware.noop

}
