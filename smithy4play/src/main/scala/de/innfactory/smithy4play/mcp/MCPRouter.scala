package de.innfactory.smithy4play.mcp

import cats.data.EitherT
import cats.implicits._
import de.innfactory.smithy4play.mcp.MCPModels._
import de.innfactory.smithy4play.{AutoRoutableController, ContextRoute, ContextRouteError, RouteResult, RoutingContext, Smithy4PlayError, Status}
import play.api.libs.json._
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Router that handles MCP (Model Context Protocol) endpoints
 * Provides /mcp endpoints for tool discovery and execution
 */
@Singleton
class MCPRouter @Inject()(
    mcpToolDiscovery: MCPToolDiscovery,
    mcpProxyService: MCPProxyService
)(implicit
    cc: ControllerComponents,
    ec: ExecutionContext
) extends AbstractController(cc) {

  private val registeredServices = scala.collection.mutable.Set[Any]()

  /**
   * Register a service to be included in MCP tool discovery
   */
  def registerService(service: Any): Unit = {
    registeredServices += service
  }

  /**
   * Lists all available MCP tools from registered services
   */
  def listTools(): Action[AnyContent] = Action.async { implicit request =>
    Future {
      val allTools = registeredServices.toSeq.flatMap { service =>
        service match {
          case s: smithy4s.Service[_] => mcpToolDiscovery.discoverTools(s)
          case _ => Seq.empty
        }
      }
      
      val response = MCPListToolsResponse(allTools)
      Ok(Json.toJson(response))
    }.recover {
      case ex: Exception =>
        InternalServerError(Json.obj(
          "error" -> "Failed to list tools",
          "message" -> ex.getMessage
        ))
    }
  }

  /**
   * Executes an MCP tool call by proxying to the original endpoint
   */
  def callTool(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[MCPCallToolRequest] match {
      case JsSuccess(toolRequest, _) =>
        mcpProxyService.executeToolCall(toolRequest, registeredServices.toSeq)
          .map {
            case Right(response) => Ok(Json.toJson(response))
            case Left(error) => BadRequest(Json.obj(
              "error" -> error.message,
              "isError" -> true
            ))
          }
          .recover {
            case ex: Exception =>
              InternalServerError(Json.obj(
                "error" -> "Tool execution failed",
                "message" -> ex.getMessage,
                "isError" -> true
              ))
          }
      
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj(
          "error" -> "Invalid request format",
          "details" -> JsError.toJson(errors),
          "isError" -> true
        )))
    }
  }

  /**
   * Provides routes for the MCP endpoints
   */
  def routes: PartialFunction[RequestHeader, Handler] = {
    case req if req.path == "/mcp/tools" && req.method == "GET" =>
      listTools()
    case req if req.path == "/mcp/call" && req.method == "POST" =>
      callTool()
  }
}