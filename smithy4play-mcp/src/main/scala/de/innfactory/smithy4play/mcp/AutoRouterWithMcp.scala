package de.innfactory.smithy4play.mcp

import com.typesafe.config.Config
import de.innfactory.smithy4play.routing.controller.ControllerRouter
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router.Routes

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContex@Singleton
class AutoRouterWithMcp @Inject() (
    mcpController: McpController
)(implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends ControllerRouter {

  override def routes: Routes = {
    val parentRoutes = super.routes

    new Routes {
      override def routes: PartialFunction[RequestHeader, Handler] = {
        parentRoutes orElse {
          case rh if rh.path == "/mcp" && rh.method == "POST" =>
            mcpController.jsonRpcEndpoint()
          case rh if rh.path == "/mcp" && rh.method == "OPTIONS" =>
            mcpController.optionsCors()
        }
      }

      override def prefix: String = ""
    }
  }
}

