package controller.middlewares

import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ EndpointResult, RouteResult, RoutingContext, Status }
import testDefinitions.test.ChangeStatusCode

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ChangeStatusCodeMiddleware @Inject() (implicit executionContext: ExecutionContext) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean =
    !r.endpointHints.has(ChangeStatusCode)

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointResult]
  ): RouteResult[EndpointResult] = {
    val res = next(r)
    res.map { r =>
      r.copy(status = Status(r.status.headers, 269))
    }
  }

}
