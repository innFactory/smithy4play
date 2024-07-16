package de.innfactory.smithy4play.routing.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.{ logger, ContextRoute, RoutingResult }
import de.innfactory.smithy4play.routing.*
import de.innfactory.smithy4play.routing.context.RoutingContext
import play.api.mvc.Result

import scala.concurrent.ExecutionContext

abstract class Smithy4PlayMiddleware {

  def skipMiddleware(r: RoutingContext): Boolean = false

  def logic(
    r: RoutingContext,
    next: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result]

  private val className = this.getClass.getName

  private[smithy4play] def middleware(
    r: RoutingContext,
    f: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result] =
    if (skipMiddleware(r)) {
      logger.debug(s"[${className}] skipping middleware")
      f(r)
    } else {
      logger.debug(s"[${className}] applying middleware")
      logic(r, f)
    }

  private[smithy4play] def construct(f: RoutingContext => RoutingResult[Result])(implicit
    ec: ExecutionContext
  ): ContextRoute[Result] =
    Kleisli { case rc: RoutingContext => middleware(rc, f) }

}
