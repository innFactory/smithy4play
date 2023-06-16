package controller.middlewares

import cats.data.EitherT
import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ ContextRouteError, RouteResult, RoutingContext }
import play.api.mvc.Result

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class TestMiddlewareImpl @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[Result]
  ): RouteResult[Result] = {
    logger.info("[TestMiddleware.logic1]")
    val r1  = r.copy(attributes = r.attributes + ("Test" -> "Test"))
    val res = next(r1)
    logger.info("[TestMiddleware.logic2]")
    res
  }

}
