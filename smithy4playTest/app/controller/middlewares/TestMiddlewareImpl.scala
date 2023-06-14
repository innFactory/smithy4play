package controller.middlewares

import cats.data.Kleisli
import de.innfactory.smithy4play.{ MiddlewareBase, RouteResult, RoutingContext }
import smithy4s.ShapeTag
import testDefinitions.test.TestMiddleware

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TestMiddlewareImpl @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override val middlewareEnableHint: Option[ShapeTag[_]]  = Some(TestMiddleware.tagInstance)
  override val middlewareDisableFlag: Option[ShapeTag[_]] = None

  override def logic: Kleisli[RouteResult, RoutingContext, RoutingContext] =
    enableLogic { r =>
      logger.info("[TestMiddleware.logic]")
      r.copy(attributes = r.attributes + ("Test" -> "Test"))
    }
}
