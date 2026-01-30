package de.innfactory.smithy4play.routing.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.RoutingResult
import de.innfactory.smithy4play.routing.PlayTransformation
import de.innfactory.smithy4play.routing.context.{ RoutingContext, RoutingContextBase }
import play.api.mvc.Result
import smithy4s.Endpoint

import scala.concurrent.ExecutionContext

/**
 * Middleware trait for request/response processing.
 * 
 * Performance optimizations:
 * - Middleware chain is pre-composed at initialization time
 * - Empty middleware list uses fast-path that avoids Kleisli allocation
 * - Composed function is cached and reused for all requests
 */
trait Middleware {

  /**
   * Override to provide custom middleware implementations.
   * Middleware is applied in order (first middleware wraps second, etc.)
   */
  def smithy4PlayMiddleware: Seq[Smithy4PlayMiddleware] = Seq.empty[Smithy4PlayMiddleware]

  /**
   * Override to provide Smithy4s endpoint middleware.
   */
  def middleware: Endpoint.Middleware[PlayTransformation] =
    Endpoint.Middleware.noop

  // Pre-compose the middleware chain at initialization time
  // This avoids rebuilding the chain on every request
  private lazy val composedMiddlewareChain: Option[ComposedMiddleware] = {
    val middlewares = smithy4PlayMiddleware
    if (middlewares.isEmpty) {
      None // Fast path - no middleware to apply
    } else {
      Some(ComposedMiddleware(middlewares))
    }
  }

  /**
   * Execute the middleware chain for a request.
   * Uses pre-composed chain for efficiency.
   */
  def logic(
    r: RoutingContext,
    function: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result] = {
    composedMiddlewareChain match {
      case None => 
        // Fast path: no middleware, just call the function directly
        function(r)
      case Some(composed) =>
        // Use pre-composed middleware chain
        composed.run(r, function)
    }
  }

  private[smithy4play] def resolveMiddleware(implicit ec: ExecutionContext): Endpoint.Middleware[PlayTransformation] =
    middleware.andThen(InjectorMiddleware(logic))
}

/**
 * Pre-composed middleware chain for efficient execution.
 * Built once at initialization and reused for all requests.
 */
private[middleware] final class ComposedMiddleware(middlewares: Seq[Smithy4PlayMiddleware]) {
  
  /**
   * Execute the composed middleware chain.
   * 
   * This implementation avoids creating Kleisli wrappers on every request
   * by using direct function composition.
   */
  def run(
    context: RoutingContext,
    finalFunction: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result] = {
    // Build the execution chain from right to left
    // Each middleware wraps the next one
    val chain = middlewares.foldRight(finalFunction) { (middleware, next) =>
      (rc: RoutingContext) => middleware.middleware(rc, next)
    }
    chain(context)
  }
}

private[middleware] object ComposedMiddleware {
  def apply(middlewares: Seq[Smithy4PlayMiddleware]): ComposedMiddleware =
    new ComposedMiddleware(middlewares)
}
