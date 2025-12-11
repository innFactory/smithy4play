package de.innfactory.smithy4play.mcp

import cats.data.EitherT
import cats.implicits.*
import com.google.inject.{Inject, Singleton}
import de.innfactory.smithy4play.mcp.server.domain.{McpError, Tool}
import de.innfactory.smithy4play.mcp.server.service.McpToolRegistryService
import de.innfactory.smithy4play.mcp.server.util.DocumentConverter.{documentToJsValue, jsValueToSmithyDocument}
import org.apache.pekko.stream.Materializer
import play.api.{Configuration, Logger, Logging}
import play.api.libs.json.*
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class McpController @Inject() (
  mcpToolRegistry: McpToolRegistryService,
  cc: ControllerComponents,
  configuration: Configuration
)(using ExecutionContext, Materializer)
    extends AbstractController(cc) with Logging {

  private val protocolVersion = configuration.getOptional[String]("mcp.protocol.version").getOrElse("2025-06-18")
  private val serverName      = configuration.getOptional[String]("mcp.server.name").getOrElse("smithy4play-mcp")
  private val serverVersion   = configuration
    .getOptional[String]("mcp.server.version")
    .orElse(sys.props.get("mcp.server.version"))
    .getOrElse("0.0.0")

  private val toolCapabilities     = Json.obj("listChanged" -> false)
  private val resourceCapabilities = Json.obj(
    "subscribe"   -> false,
    "listChanged" -> false
  )
  private val promptCapabilities   = Json.obj("listChanged" -> false)
  private val samplingCapabilities = Json.obj("samplingMethods" -> Json.arr())
  private val loggingCapabilities  = Json.obj("listChanged" -> false)

  def optionsCors(): Action[AnyContent] = Action { implicit request =>
    McpHttpUtil.addCorsAndStreamingHeaders(NoContent)
  }

  def jsonRpcEndpoint(): Action[JsValue] = Action.async(parse.json(maxLength = 10 * 1024 * 1024)) { implicit request =>
    handleJsonRpc(request)
  }

  def handleJsonRpc(request: Request[JsValue]): Future[Result] = {
    val jsonRpcRequest = JsonRpcParser.parse(request.body)
    logger.info(s"[MCP JSON-RPC] Request: method=${jsonRpcRequest.method}, id=${jsonRpcRequest.id}")
    handleJsonRpcRequest(jsonRpcRequest, request)
  }

  private def handleJsonRpcRequest(
    req: JsonRpcParser.JsonRpcRequest,
    request: play.api.mvc.Request[?]
  ): Future[Result] =
    req.validate match {
      case Left(error) => Future.successful(McpHttpUtil.jsonRpcError(req.id, error.code, error.message))
      case Right(_)    =>
        req.method match {
          case Some("initialize")                => handleInitializeRequest(req.id, req.params)
          case Some("notifications/initialized") => handleInitializedNotification(req.id)
          case Some("tools/list")                => handleToolsList(req.id, req.params)
          case Some("tools/call")                => handleToolsCall(req.id, req.params, request)
          case Some("ping")                      => Future.successful(McpHttpUtil.jsonRpcSuccess(req.id, Json.obj()))
          case Some(m)                           => Future.successful(McpHttpUtil.jsonRpcError(req.id, -32601, s"Method not found: $m"))
          case None                              => Future.successful(McpHttpUtil.jsonRpcError(req.id, -32600, "Invalid Request: missing method"))
        }
    }

  private def handleInitializeRequest(
    id: Option[JsValue],
    params: Option[JsValue]
  ): Future[Result] = {
    val capabilities = Json.obj(
      "tools"     -> toolCapabilities,
      "resources" -> resourceCapabilities,
      "prompts"   -> promptCapabilities,
      "sampling"  -> samplingCapabilities,
      "logging"   -> loggingCapabilities
    )

    val result = Json.obj(
      "protocolVersion" -> protocolVersion,
      "serverInfo"      -> Json.obj(
        "name"    -> serverName,
        "version" -> serverVersion
      ),
      "capabilities"    -> capabilities
    )

    Future.successful(McpHttpUtil.jsonRpcSuccess(id, result))
  }

  private def handleInitializedNotification(
    id: Option[JsValue]
  ): Future[Result] =
    Future.successful(McpHttpUtil.jsonRpcSuccess(id, JsNull))

  private def handleToolsList(
    id: Option[JsValue],
    params: Option[JsValue]
  ): Future[Result] = {
    val tools     = mcpToolRegistry.getAllTools
    val toolsJson = tools.map(toolToJson)
    logger.info(s"[MCP] Reporting ${tools.size} available tools")

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
      "inputSchema" -> documentToJsValue(tool.inputSchema),
      "outputSchema" -> documentToJsValue(tool.outputSchema)
    )

  private def handleToolsCall(
    id: Option[JsValue],
    params: Option[JsValue],
    request: play.api.mvc.Request[?]
  ): Future[Result] = {
    val result = for {
      toolName  <- EitherT.fromOption[Future](
                     params.flatMap(p => (p \ "name").asOpt[String]),
                     McpError.InvalidJsonDocument("Missing tool name")
                   )
      arguments <- EitherT.fromOption[Future](
                     params.flatMap(p => (p \ "arguments").toOption),
                     McpError.InvalidJsonDocument("Missing required arguments")
                   )
      smithyArgs = jsValueToSmithyDocument(arguments)
      response  <- mcpToolRegistry.callTool(toolName, Some(smithyArgs), request)
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

  private def buildToolCallErrorResponse(
    id: Option[JsValue],
    error: de.innfactory.smithy4play.mcp.server.domain.McpError
  ): Result =
    McpHttpUtil.jsonRpcError(id, error.jsonRpcErrorCode, error.message)
}
