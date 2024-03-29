package de.innfactory.smithy4play.middleware

import cats.data.EitherT
import de.innfactory.smithy4play
import de.innfactory.smithy4play.{ RouteResult, RoutingContext, Smithy4PlayError }
import smithy.api
import smithy.api.{ Auth, HttpBearerAuth }
import smithy4s.Blob
import smithy4s.http.HttpResponse

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ValidateAuthMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean = {
    val serviceAuthHints: Option[api.Auth.Type] =
      r.serviceHints
        .get(HttpBearerAuth.tagInstance)
        .map(_ =>
          Auth(Set(smithy.api.AuthTraitReference(smithy4s.ShapeId(namespace = "smithy.api", name = "httpBearerAuth"))))
        )
    for {
      authSet <- r.endpointHints.get(Auth.tag) orElse serviceAuthHints
      _       <- authSet.value.find(_.value.name == HttpBearerAuth.id.name)
    } yield r.headers.contains("Authorization")
  }.getOrElse(true)

  override def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[HttpResponse[Blob]]
  ): RouteResult[HttpResponse[Blob]] =
    EitherT.leftT[Future, HttpResponse[Blob]](
      Smithy4PlayError("Unauthorized", status = smithy4play.Status(Map.empty, 401), contentType = "application/json")
    )

}
