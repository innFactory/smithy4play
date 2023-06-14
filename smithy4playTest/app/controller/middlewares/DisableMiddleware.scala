package controller.middlewares

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.{ ContextRouteError, MiddlewareBase, RouteResult, RoutingContext }
import smithy4s.{ Hint, Hints, Newtype, ShapeTag }
import testDefinitions.test.TestMiddleware

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class TestMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override val middlewareEnableHint: Option[ShapeTag[_]]  = Some(TestMiddleware.tagInstance)
  override val middlewareDisableFlag: Option[ShapeTag[_]] = None

  override def logic: Kleisli[RouteResult, RoutingContext, RoutingContext] = {
    logger.info("[TestMiddleware.logic]")
    enableLogic { r =>
      r.copy(attributes = Map("Test" -> "Test"))
    }
  }
}
