package controllers

import cats.data.EitherT
import cats.data.Kleisli
import de.innfactory.smithy4play.{AutoRouting, ContextRoute, RoutingContext}
import play.api.mvc.ControllerComponents
import testDefinitions.test._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
@AutoRouting
class MCPExampleController @Inject()(implicit
    cc: ControllerComponents,
    ec: ExecutionContext
) extends MCPExampleControllerService[ContextRoute] {

  override def getUser(id: String): ContextRoute[GetUserResponse] =
    Kleisli { rc =>
      // Mock implementation
      val response = GetUserResponse(
        id = id,
        name = s"User $id",
        email = Some(s"user$id@example.com")
      )
      EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](response)
    }

  override def createUser(body: CreateUserBody): ContextRoute[CreateUserResponse] =
    Kleisli { rc =>
      // Mock implementation - generate a random ID
      val newId = java.util.UUID.randomUUID().toString
      val response = CreateUserResponse(
        id = newId,
        name = body.name,
        email = body.email
      )
      EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](response)
    }
}