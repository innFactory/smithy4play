package controller.middlewares

import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ EndpointResult, RouteResult, RoutingContext }

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TestMiddlewareImpl @Inject() (implicit executionContext: ExecutionContext) extends MiddlewareBase {

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointResult]
  ): RouteResult[EndpointResult] = {
    logger.info("[TestMiddleware.logic1]")
    val r1  = r.copy(attributes = r.attributes + ("Test" -> "Test"))
    val res = next(r1)
    logger.info("[TestMiddleware.logic2]")
    res.map { r =>
      logger.info("[TestMiddleware.logic3]")
      r.copy(headers = r.headers + ("EndpointResultTest" -> "Test123"))
    }
  }

}
