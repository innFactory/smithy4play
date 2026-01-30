package controller.middlewares

import cats.data.EitherT
import de.innfactory.smithy4play
import de.innfactory.smithy4play.routing.context.{ RoutingContext, RoutingContextBase }
import de.innfactory.smithy4play.routing.middleware.{ Middleware, Smithy4PlayMiddleware }
import de.innfactory.smithy4play.{ ContextRoute, RoutingResult }
import smithy.api
import smithy.api.{ Auth, HttpBearerAuth }
import smithy4s.Blob
import smithy4s.http.HttpResponse
import play.api.mvc.Result
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ValidateAuthMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends Smithy4PlayMiddleware {

  override def skipMiddleware(r: RoutingContext): Boolean = {
    val serviceAuthHints: Option[api.Auth.Type] =
      r.serviceHints
        .get(using HttpBearerAuth.tagInstance)
        .map(_ =>
          Auth(Set(smithy.api.AuthTraitReference(smithy4s.ShapeId(namespace = "smithy.api", name = "httpBearerAuth"))))
        )
    for {
      authSet <- r.endpointHints.get(using Auth.tag) orElse serviceAuthHints
      _       <- authSet.value.find(_.value.name == HttpBearerAuth.id.name)
    } yield r.headers.contains("Authorization")
  }.getOrElse(true)

  def logic(
    r: RoutingContext,
    next: RoutingContext => RoutingResult[Result]
  )(implicit ec: ExecutionContext): RoutingResult[Result] = {
    println("ValidateAuthMiddleware logic")
    EitherT.leftT[Future, Result](
      testDefinitions.test.UnauthorizedError("Unauthorized")
    )
  }

}
