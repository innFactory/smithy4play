package de.innfactory.smithy4play.mcp

import cats.data.EitherT
import cats.implicits.*
import com.google.inject.{ Inject, Singleton }
import de.innfactory.smithy4play.mcp.server.domain.Tool
import de.innfactory.smithy4play.mcp.server.service.McpToolRegistryService
import de.innfactory.smithy4play.mcp.server.util.DocumentConverter.{ documentToJsValue, jsValueToSmithyDocument }
import org.apache.pekko.stream.Materializer
import play.api.libs.json.*
import play.api.mvc.*
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class McpController @Inject() (
  mcpToolRegistry: McpToolRegistryService,
  cc: ControllerComponents
)(using ExecutionContext, Materializer)
    extends AbstractController(cc) {

  private val logger           = Logger(this.getClass)
  private val SERVER_NAME      = "smithy4play-mcp-server"
  private val SERVER_VERSION   = "1.0.0"
  private val PROTOCOL_VERSION = "2024-11-05"

  private final case class JsonRpcRequest(
    jsonrpcVersion: Option[String],
    id: Option[JsValue],
    method: Option[String],
    params: Option[JsValue]
  )

  def optionsCors(): Action[AnyContent] = Action { implicit request =>
    McpHttpUtil.addCorsAndStreamingHeaders(NoContent)
  }

  def jsonRpcEndpoint(): Action[JsValue] = Action.async(parse.json(maxLength = 10 * 1024 * 1024)) { implicit request =>
    handleJsonRpc(request)
  }

  /** Handle JSON-RPC after JSON body has been parsed. */
  def handleJsonRpc(request: Request[JsValue]): Future[Result] = {
    val jsonRpcRequest = parseJsonRpcRequest(request.body)
    val sessionId      = request.headers.get("X-Session-ID")

    logger.info(s"[MCP JSON-RPC] Request: method=${jsonRpcRequest.method}, id=${jsonRpcRequest.id}, session=$sessionId")

    handleJsonRpcRequest(jsonRpcRequest, sessionId, request)
  }

  private def parseJsonRpcRequest(body: JsValue): JsonRpcRequest =
    JsonRpcRequest(
      jsonrpcVersion = (body \ "jsonrpc").asOpt[String],
      id = (body \ "id").toOption,
      method = (body \ "method").asOpt[String],
      params = (body \ "params").toOption
    )

  private def handleJsonRpcRequest(
    req: JsonRpcRequest,
    sessionId: Option[String],
    request: play.api.mvc.Request[?]
  ): Future[Result] =
    if (!req.jsonrpcVersion.contains("2.0"))
      Future.successful(McpHttpUtil.jsonRpcError(req.id, -32600, "Invalid Request: jsonrpc must be 2.0"))
    else
      req.method match {
        case Some("initialize")                => handleInitializeRequest(req.id, req.params, sessionId)
        case Some("notifications/initialized") => handleInitializedNotification(req.id, sessionId)
        case Some("tools/list")                => handleToolsList(req.id, req.params, sessionId)
        case Some("tools/call")                => handleToolsCall(req.id, req.params, sessionId, request)
        case Some("ping")                      => Future.successful(McpHttpUtil.jsonRpcSuccess(req.id, Json.obj()))
        case Some(m)                           => Future.successful(McpHttpUtil.jsonRpcError(req.id, -32601, s"Method not found: $m"))
        case None                              => Future.successful(McpHttpUtil.jsonRpcError(req.id, -32600, "Invalid Request: missing method"))
      }

  private def handleInitializeRequest(
    id: Option[JsValue],
    params: Option[JsValue],
    sessionId: Option[String]
  ): Future[Result] = Future.successful(Results.Ok)

  private def handleInitializedNotification(
    id: Option[JsValue],
    sessionId: Option[String]
  ): Future[Result] =
    Future.successful(McpHttpUtil.jsonRpcSuccess(id, JsNull))

  private def handleToolsList(
    id: Option[JsValue],
    params: Option[JsValue],
    sessionId: Option[String]
  ): Future[Result] = {
    val tools     = mcpToolRegistry.getAllTools
    val toolsJson = tools.map(toolToJson)

    logger.info(s"[MCP] Reporting ${tools.size} available tools")
    tools.headOption.foreach(tool => logger.debug(s"[MCP] Tools: ${documentToJsValue(tool.inputSchema)}"))

    Future.successful(
      McpHttpUtil.jsonRpcSuccess(
        id,
        Json.obj("tools" -> toolsJson)
      )
    )
  }

  private def toolToJson(tool: Tool): JsObject =
    Json.obj(
      "name"        -> tool.name,
      "description" -> tool.description.getOrElse(""),
      "inputSchema" -> documentToJsValue(tool.inputSchema)
    )

  private def handleToolsCall(
    id: Option[JsValue],
    params: Option[JsValue],
    sessionId: Option[String],
    request: play.api.mvc.Request[?]
  ): Future[Result] = {
    val result = for {
      toolName  <- EitherT.fromOption[Future](
                     params.flatMap(p => (p \ "name").asOpt[String]),
                     "Missing tool name"
                   )
      arguments  = params.flatMap(p => (p \ "arguments").toOption)
      smithyArgs = arguments.map(jsValueToSmithyDocument)
      response  <- mcpToolRegistry.callTool(toolName, smithyArgs, request).leftMap(_.message)
    } yield buildToolCallSuccessResponse(id, response)

    result.fold(
      error => buildToolCallErrorResponse(id, error),
      identity
    )
  }

  private def buildToolCallSuccessResponse(id: Option[JsValue], result: String): Result =
    McpHttpUtil.jsonRpcSuccess(
      id,
      Json.obj(
        "content" -> Json.arr(
          Json.obj(
            "type" -> "text",
            "text" -> result
          )
        )
      )
    )

  private def buildToolCallErrorResponse(id: Option[JsValue], error: String): Result =
    McpHttpUtil.jsonRpcSuccess(
      id,
      Json.obj(
        "content" -> Json.arr(
          Json.obj(
            "type" -> "text",
            "text" -> s"Error: $error"
          )
        ),
        "isError" -> true
      )
    )
}
