package controller.middlewares

import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ EndpointResult, RouteResult, RoutingContext }

class TestMiddlewareImpl extends MiddlewareBase {

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointResult]
  ): RouteResult[EndpointResult] = {
    logger.info("[TestMiddleware.logic1]")
    val r1  = r.copy(attributes = r.attributes + ("Test" -> "Test"))
    val res = next(r1)
    logger.info("[TestMiddleware.logic2]")
    res
  }

}
