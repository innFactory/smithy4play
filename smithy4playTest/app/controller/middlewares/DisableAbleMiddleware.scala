package controller.middlewares

import cats.data.EitherT
import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ ContextRouteError, RouteResult, RoutingContext }
import play.api.mvc.Result
import testDefinitions.test.DisableTestMiddleware

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class DisableAbleMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean =
    r.hasHints(DisableTestMiddleware)

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[Result]
  ): RouteResult[Result] = {
    logger.info("[DisableAbleMiddleware.logic1]")
    val r1  = r.copy(attributes = r.attributes + ("Not" -> "Disabled"))
    val res = next(r1)
    logger.info("[DisableAbleMiddleware.logic2]")
    res
  }

}
