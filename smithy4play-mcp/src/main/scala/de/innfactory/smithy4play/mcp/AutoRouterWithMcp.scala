package de.innfactory.smithy4play.mcp

import cats.data.EitherT
import cats.implicits.toBifunctorOps
import com.typesafe.config.Config
import de.innfactory.smithy4play.AutoRouter
import de.innfactory.smithy4play.mcp.common.MCPCommon.ContentTypes.APPLICATION_JSON
import de.innfactory.smithy4play.mcp.common.MCPCommon.HttpMethods.*
import de.innfactory.smithy4play.mcp.common.MCPCommon.MCP_ENDPOINT
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.routing.Router.Routes

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AutoRouterWithMcp @Inject() (implicit
  mcpController: McpController,
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends AutoRouter {

  protected def validateToken(authHeader: Option[String]): EitherT[Future, String, Unit] = {
    val res: Either[String, Unit] = authHeader match {
      case Some(_) => Right(())
      case None    => Left("Missing Authorization header")
    }
    EitherT.fromEither[Future](res)
  }

  override val routes: Routes = {
    val parentRoutes = super.routes

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(r: RequestHeader): Boolean =
        parentRoutes.isDefinedAt(r) || (r.path == MCP_ENDPOINT && (r.method == POST || r.method == GET || r.method == OPTIONS))

      override def apply(v1: RequestHeader): Handler =
        if (v1.path == MCP_ENDPOINT && v1.method == POST) {
          Action.async { implicit request =>
            (for {
              _          <- validateToken(request.headers.get("Authorization"))
              jsonBody   <- parseJsonBody(request)
              jsonRequest = request.map(_ => jsonBody)
              result     <- EitherT.right[String](mcpController.handleJsonRpc(jsonRequest))
            } yield result).fold(
              error => McpHttpUtil.unauthorizedError(error),
              identity
            )
          }
        } else if (v1.path == MCP_ENDPOINT && v1.method == GET) {
          mcpController.sseStream()
        } else if (v1.path == MCP_ENDPOINT && v1.method == OPTIONS) {
          mcpController.optionsCors()
        } else {
          parentRoutes(v1)
        }

      private def parseJsonBody(
        request: play.api.mvc.Request[play.api.mvc.AnyContent]
      ): EitherT[Future, String, play.api.libs.json.JsValue] =
        request.contentType match {
          case Some(APPLICATION_JSON) =>
            EitherT.fromEither[Future](
              scala.util
                .Try(request.body.asJson.getOrElse(Json.obj()))
                .toEither
                .leftMap(_ => "Parse error")
            )
          case _                      =>
            EitherT.leftT[Future, play.api.libs.json.JsValue](s"Content-Type must be ${APPLICATION_JSON}")
        }
    }
  }
}
