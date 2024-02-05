package controller.middlewares

import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ EndpointRequest, RouteResult, RoutingContext, Status }
import smithy4s.http.Metadata
import testDefinitions.test.ChangeStatusCode

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ChangeStatusCodeMiddleware @Inject() (implicit executionContext: ExecutionContext) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean =
    !r.endpointHints.has(ChangeStatusCode)

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointRequest]
  ): RouteResult[EndpointRequest] = {
    val res = next(r)
    res.map { r =>
      r.copy(metadata = Metadata.empty.copy(statusCode = Some(269)))
    }
  }

}
