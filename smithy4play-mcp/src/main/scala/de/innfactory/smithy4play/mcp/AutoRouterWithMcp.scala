package de.innfactory.smithy4play.mcp

import com.typesafe.config.Config
import de.innfactory.smithy4play.AutoRouter
import play.api.Application
import play.api.mvc.{ Action, ControllerComponents }
import play.api.routing.Router.Routes

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import cats.data.EitherT

@Singleton
class AutoRouterWithMcp @Inject() (
  mcpController: McpController
)(implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends AutoRouter {

  protected def validateToken(authHeader: Option[String]): EitherT[Future, String, Unit] = {
    val res: Either[String, Unit] = authHeader match {
      case Some(h) if h.startsWith("Bearer ") || h.startsWith("bearer ") =>
        val token = h.stripPrefix("Bearer ").stripPrefix("bearer ")
        if (token.nonEmpty) Right(()) else Left("Empty token")
      case Some(_)                                                       => Left("Invalid Authorization header format. Expected 'Bearer <token>'")
      case None                                                          => Left("Missing Authorization header")
    }
    EitherT.fromEither[Future](res)
  }

  override def routes: Routes =
    super.routes orElse {
      case rh if rh.path == "/mcp" && rh.method == "POST"    =>
        Action.async(parse.json(maxLength = 10 * 1024 * 1024)) { implicit request =>
          validateToken(request.headers.get("Authorization")).value.flatMap {
            case Left(err) => Future.successful(McpHttpUtil.unauthorizedError(err))
            case Right(_)  => mcpController.handleJsonRpc(request)
          }
        }
      case rh if rh.path == "/mcp" && rh.method == "OPTIONS" =>
        mcpController.optionsCors()
    }
}
