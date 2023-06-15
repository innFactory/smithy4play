package controller.middlewares

import cats.data.EitherT
import de.innfactory.smithy4play.{ ContextRouteError, MiddlewareBase, RouteResult, RoutingContext }
import testDefinitions.test.DisableTestMiddleware

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class DisableAbleMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean = {
    r.serviceHints.has(DisableTestMiddleware.tagInstance) || r.endpointHints.has(DisableTestMiddleware.tagInstance)
  }

  override def logic(r: RoutingContext): RouteResult[RoutingContext] = EitherT.rightT[Future, ContextRouteError](
    r.copy(attributes = r.attributes + ("Not" -> "Disabled"))
  )
}
