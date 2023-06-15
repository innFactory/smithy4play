package controller.middlewares

import cats.data.EitherT
import de.innfactory.smithy4play.{ContextRouteError, MiddlewareBase, RouteResult, RoutingContext}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TestMiddlewareImpl @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override def logic(r: RoutingContext): RouteResult[RoutingContext] = EitherT.rightT[Future, ContextRouteError]({
    logger.info("[TestMiddleware.logic]")
    r.copy(attributes = r.attributes + ("Test" -> "Test"))
  })
}
