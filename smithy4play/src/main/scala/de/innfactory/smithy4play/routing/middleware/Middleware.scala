package de.innfactory.smithy4play.routing.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.RoutingResult
import de.innfactory.smithy4play.routing.PlayTransformation
import de.innfactory.smithy4play.routing.context.{ RoutingContext, RoutingContextBase }
import play.api.mvc.Result
import smithy4s.Endpoint

import scala.concurrent.ExecutionContext

trait Middleware {

  def logic(
    r: RoutingContext,
    function: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result] = {
    val initial: Kleisli[RoutingResult, RoutingContext, Result] = Kleisli((rc: RoutingContext) => function(rc))
    smithy4PlayMiddleware.foldRight(initial)((a, b) => a.construct(b.run)).run(r)
  }

  def smithy4PlayMiddleware: Seq[Smithy4PlayMiddleware] = Seq.empty[Smithy4PlayMiddleware]

  private[smithy4play] def resolveMiddleware(implicit ec: ExecutionContext) =
    middleware.andThen(InjectorMiddleware(logic))

  def middleware: Endpoint.Middleware[PlayTransformation] =
    Endpoint.Middleware.noop

}
