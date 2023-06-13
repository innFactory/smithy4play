package controller.middlewares

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.{ ContextRouteError, MiddlewareBase, RouteResult, RoutingContext }
import smithy4s.Hint
import testDefinitions.test.TestMiddleware

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class TestMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {
  override val hint: Hint = TestMiddleware()

  override def logic: Kleisli[RouteResult, RoutingContext, RoutingContext] = Kleisli { context =>
    logger.info("[TestMiddleware.logic]")
    EitherT.rightT[Future, ContextRouteError](context)
  }
}
