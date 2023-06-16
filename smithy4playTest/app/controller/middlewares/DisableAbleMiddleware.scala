package controller.middlewares

import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ EndpointResult, RouteResult, RoutingContext }
import testDefinitions.test.DisableTestMiddleware

import javax.inject.Singleton

@Singleton
class DisableAbleMiddleware extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean =
    r.hasHints(DisableTestMiddleware)

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointResult]
  ): RouteResult[EndpointResult] = {
    logger.info("[DisableAbleMiddleware.logic1]")
    val r1  = r.copy(attributes = r.attributes + ("Not" -> "Disabled"))
    val res = next(r1)
    logger.info("[DisableAbleMiddleware.logic2]")
    res
  }

}
