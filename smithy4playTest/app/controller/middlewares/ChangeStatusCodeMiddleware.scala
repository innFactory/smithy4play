package controller.middlewares

import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{RouteResult, RoutingContext}
import smithy4s.Blob
import smithy4s.http.HttpResponse
import testDefinitions.test.ChangeStatusCode

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ChangeStatusCodeMiddleware @Inject() (implicit executionContext: ExecutionContext) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean =
    !r.endpointHints.has(ChangeStatusCode)

  override protected def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[HttpResponse[Blob]]
  ): RouteResult[HttpResponse[Blob]] = {
    val res = next(r)
    res.map { r =>
      r.copy(statusCode = 269)
    }
  }

}
