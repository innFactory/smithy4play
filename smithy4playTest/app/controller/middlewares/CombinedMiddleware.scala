package controller.middlewares

import cats.data.Kleisli
import de.innfactory.smithy4play.{ MiddlewareBase, RouteResult, RoutingContext }
import smithy4s.ShapeTag
import testDefinitions.test.{ DisableTestMiddleware, TestMiddleware }

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CombinedMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override val middlewareEnableHint: Option[ShapeTag[_]]  = Some(TestMiddleware.tagInstance)
  override val middlewareDisableFlag: Option[ShapeTag[_]] = Some(DisableTestMiddleware.tagInstance)

  override def logic: Kleisli[RouteResult, RoutingContext, RoutingContext] = {
    combinedLogic { r =>
    logger.info("[CombinedMiddleware.logic]")
      r.copy(attributes = r.attributes + ("Combined" -> "Middleware"))
    }
  }
}
