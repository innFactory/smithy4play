package controller.middlewares

import cats.data.EitherT
import de.innfactory.smithy4play
import de.innfactory.smithy4play.routing.context.{ RoutingContext, RoutingContextBase }
import de.innfactory.smithy4play.routing.middleware.{ Middleware, Smithy4PlayMiddleware }
import de.innfactory.smithy4play.{ ContextRoute, RoutingResult }
import play.api.mvc.Result
import smithy.api
import smithy.api.{ Auth, HttpBearerAuth }
import smithy4s.Blob
import smithy4s.http.HttpResponse

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AddHeaderMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends Smithy4PlayMiddleware {

  override def skipMiddleware(r: RoutingContext): Boolean = {
    false
  }

  def logic(
    r: RoutingContext,
    next: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result] = {
   next(r).map(v => v.withHeaders(("endpointresulttest", "test")))
  }

}
