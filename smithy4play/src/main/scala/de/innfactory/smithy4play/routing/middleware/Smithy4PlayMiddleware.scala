package de.innfactory.smithy4play.routing.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.{ logger, ContextRoute, RoutingResult }
import de.innfactory.smithy4play.routing.*
import de.innfactory.smithy4play.routing.context.RoutingContext
import play.api.Logging
import play.api.mvc.Result

import scala.concurrent.ExecutionContext

/** Base class for custom middleware implementations.
  *
  * Extend this class to add custom request/response processing logic.
  *
  * Example:
  * {{{
  * class AuthMiddleware extends Smithy4PlayMiddleware {
  *   override def logic(
  *     r: RoutingContext,
  *     next: RoutingContext => RoutingResult[Result]
  *   )(implicit ec: ExecutionContext): RoutingResult[Result] = {
  *     // Check authentication
  *     if (isAuthenticated(r)) {
  *       next(r)
  *     } else {
  *       EitherT.leftT(UnauthorizedException())
  *     }
  *   }
  * }
  * }}}
  */
abstract class Smithy4PlayMiddleware extends Logging {

  /** Override to skip middleware for certain requests. Default: apply middleware to all requests.
    */
  def skipMiddleware(r: RoutingContext): Boolean = false

  /** Called when middleware is skipped. Override for custom telemetry.
    * @return
    *   The value passed in (for chaining)
    */
  def middlewareSkippingTelemetry(b: Boolean): Boolean = b

  /** Implement the middleware logic.
    *
    * @param r
    *   The routing context for the current request
    * @param next
    *   The next handler in the chain (call this to continue processing)
    * @return
    *   The result of processing
    */
  def logic(
    r: RoutingContext,
    next: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result]

  /** Internal method that handles skip logic and delegates to user-defined logic.
    */
  private[smithy4play] def middleware(
    r: RoutingContext,
    f: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result] =
    if (skipMiddleware(r)) {
      middlewareSkippingTelemetry(true)
      if (logger.isDebugEnabled) {
        logger.debug(s"skipping middleware")
      }
      f(r)
    } else {
      middlewareSkippingTelemetry(false)
      if (logger.isDebugEnabled) {
        logger.debug(s"[applying middleware")
      }
      logic(r, f)
    }

  /** Construct a ContextRoute from this middleware. Used internally for Kleisli-based composition.
    */
  private[smithy4play] def construct(f: RoutingContext => RoutingResult[Result])(implicit
    ec: ExecutionContext
  ): ContextRoute[Result] =
    Kleisli { case rc: RoutingContext => middleware(rc, f) }

}
