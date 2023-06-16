package de.innfactory.smithy4play.middleware

import cats.data.EitherT
import de.innfactory.smithy4play.{ EndpointResult, RouteResult, RoutingContext, Smithy4PlayError }
import smithy.api.{ Auth, HttpBearerAuth }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ValidateAuthMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean = {
    val serviceAuthHints = r.serviceHints.get(HttpBearerAuth.tagInstance).map(_ => Auth(Set(HttpBearerAuth.id.show)))
    for {
      authSet <- r.endpointHints.get(Auth.tag) orElse serviceAuthHints
      _       <- authSet.value.find(_.value == HttpBearerAuth.id.show)
    } yield r.headers.contains("Authorization")
  }.getOrElse(true)

  override def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointResult]
  ): RouteResult[EndpointResult] =
    EitherT.leftT[Future, EndpointResult](Smithy4PlayError("Unauthorized", 401))

}
