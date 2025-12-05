package de.innfactory.smithy4play.mcp

import com.typesafe.config.Config
import de.innfactory.smithy4play.AutoRouter
import play.api.Application
import play.api.mvc.*
import play.api.routing.Router.Routes

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import cats.data.EitherT
import play.api.libs.json.Json

@Singleton
class AutoRouterWithMcp @Inject() (implicit
  mcpController: McpController,
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends AutoRouter {

  private def validateToken(authHeader: Option[String]): EitherT[Future, String, Unit] = {
    val res: Either[String, Unit] = authHeader match {
      case Some(h) if h.startsWith("Bearer ") || h.startsWith("bearer ") =>
        val token = h.stripPrefix("Bearer ").stripPrefix("bearer ")
        if (token == "test-token") Right(()) else Left("Invalid token")
      case Some(_)                                                       => Left("Invalid Authorization header format. Expected 'Bearer <token>'")
      case None                                                          => Left("Missing Authorization header")
    }
    EitherT.fromEither[Future](res)
  }

  override val routes: Routes = {
    val parentRoutes = super.routes

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(x: RequestHeader): Boolean =
        (x.path == "/mcp" && (x.method == "POST" || x.method == "OPTION")) || parentRoutes.isDefinedAt(x)

      override def apply(v1: RequestHeader): Handler =
        // Check for MCP routes first
        if (v1.path == "/mcp" && v1.method == "POST") {
          Action.async { implicit request =>
            validateToken(request.headers.get("Authorization")).value.flatMap {
              case Left(err) => Future.successful(McpHttpUtil.unauthorizedError(err))
              case Right(_)  =>
                request.contentType match {
                  case Some("application/json") =>
                    try {
                      val jsonBody    = request.body.asJson.getOrElse(Json.obj())
                      val jsonRequest = request.map(_ => jsonBody)
                      mcpController.handleJsonRpc(jsonRequest)
                    } catch {
                      case e: Exception =>
                        Future.successful(McpHttpUtil.jsonRpcError(None, -32700, "Parse error"))
                    }
                  case _                        =>
                    Future.successful(McpHttpUtil.jsonRpcError(None, -32700, "Content-Type must be application/json"))
                }
            }
          }
        } else if (v1.path == "/mcp" && v1.method == "OPTIONS") {
          mcpController.optionsCors()
        } else {
          // Fall back to the parent router
          parentRoutes(v1)
        }
    }
  }
}
