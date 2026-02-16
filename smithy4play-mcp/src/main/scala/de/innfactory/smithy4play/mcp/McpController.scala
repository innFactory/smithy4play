package de.innfactory.smithy4play.mcp

import cats.data.EitherT
import cats.implicits.*
import com.google.inject.{ Inject, Singleton }
import de.innfactory.smithy4play.mcp.server.domain.{ McpError, Tool, ToolAnnotations }
import de.innfactory.smithy4play.mcp.server.service.McpToolRegistryService
import de.innfactory.smithy4play.mcp.server.util.DocumentConverter.{ documentToJsValue, jsValueToSmithyDocument }
import org.apache.pekko.stream.Materializer
import play.api.{ Configuration, Logging }
import play.api.libs.json.*
import play.api.mvc.*

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class McpController @Inject() (
  mcpToolRegistry: McpToolRegistryService,
  cc: ControllerComponents,
  configuration: Configuration
)(using ExecutionContext, Materializer)
    extends AbstractController(cc)
    with Logging {

  private val protocolVersion = configuration.getOptional[String]("mcp.protocol.version").getOrElse("2025-06-18")
  private val serverName      = configuration.getOptional[String]("mcp.server.name").getOrElse("smithy4play-mcp")
  private val serverVersion   = configuration
    .getOptional[String]("mcp.server.version")
    .orElse(sys.props.get("mcp.server.version"))
    .getOrElse("0.0.0")
  private val serverTitle     = configuration.getOptional[String]("mcp.server.title")
  private val instructions    = configuration.getOptional[String]("mcp.server.instructions")

  private val capabilities = Json.obj(
    "tools" -> Json.obj("listChanged" -> false)
  )

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
          case Some("initialize")                => handleInitializeRequest(req.id)
          case Some("notifications/initialized") => handleInitializedNotification()
          case Some("tools/list")                => handleToolsList(req.id)
          case Some("tools/call")                => handleToolsCall(req.id, req.params, request)
          case Some("ping")                      => Future.successful(McpHttpUtil.jsonRpcSuccess(req.id, Json.obj()))
          case Some(m)                           =>
            Future.successful(McpHttpUtil.jsonRpcError(req.id, -32601, s"Method not found: $m"))
          case None                              =>
            Future.successful(McpHttpUtil.jsonRpcError(req.id, -32600, "Invalid Request: missing method"))
        }
    }

  private def handleInitializeRequest(id: Option[JsValue]): Future[Result] = {
    val serverInfo = Json
      .obj(
        "name"    -> serverName,
        "version" -> serverVersion
      )
      .deepMerge(optionalField("title", serverTitle))

    val result = Json
      .obj(
        "protocolVersion" -> protocolVersion,
        "serverInfo"      -> serverInfo,
        "capabilities"    -> capabilities
      )
      .deepMerge(optionalField("instructions", instructions))

    Future.successful(McpHttpUtil.jsonRpcSuccess(id, result))
  }

  private def handleInitializedNotification(): Future[Result] =
    Future.successful(McpHttpUtil.accepted())

  private def handleToolsList(id: Option[JsValue]): Future[Result] = {
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

  private def toolToJson(tool: Tool): JsObject = {
    val base = Json.obj(
      "name"        -> tool.name,
      "inputSchema" -> documentToJsValue(tool.inputSchema)
    )

    val withDescription  = tool.description.fold(base)(d => base + ("description" -> JsString(d)))
    val withTitle        = tool.title.fold(withDescription)(t => withDescription + ("title" -> JsString(t)))
    val withOutputSchema =
      tool.outputSchema.fold(withTitle)(s => withTitle + ("outputSchema" -> documentToJsValue(s)))
    val withAnnotations  =
      tool.annotations.fold(withOutputSchema)(a => withOutputSchema + ("annotations" -> annotationsToJson(a)))

    withAnnotations
  }

  private def annotationsToJson(annotations: ToolAnnotations): JsObject = {
    val fields = List(
      annotations.title.map("title" -> JsString(_)),
      annotations.readOnlyHint.map("readOnlyHint" -> JsBoolean(_)),
      annotations.destructiveHint.map("destructiveHint" -> JsBoolean(_)),
      annotations.idempotentHint.map("idempotentHint" -> JsBoolean(_)),
      annotations.openWorldHint.map("openWorldHint" -> JsBoolean(_))
    ).flatten

    JsObject(fields)
  }

  private def handleToolsCall(
    id: Option[JsValue],
    params: Option[JsValue],
    request: play.api.mvc.Request[?]
  ): Future[Result] = {
    val result = for {
      toolName <- EitherT.fromOption[Future](
                    params.flatMap(p => (p \ "name").asOpt[String]),
                    McpError.InvalidJsonDocument("Missing tool name"): McpError
                  )
      arguments = params.flatMap(p => (p \ "arguments").toOption).map(jsValueToSmithyDocument)
      response <- mcpToolRegistry.callTool(toolName, arguments, request)
    } yield buildCallToolResult(id, response)

    result.fold(
      error => handleToolCallError(id, error),
      identity
    )
  }

  private def buildCallToolResult(id: Option[JsValue], result: String): Result =
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

  private def buildCallToolErrorResult(id: Option[JsValue], error: McpError): Result =
    McpHttpUtil.jsonRpcSuccess(
      id,
      Json.obj(
        "content" -> Json.arr(
          Json.obj(
            "type" -> "text",
            "text" -> error.message
          )
        ),
        "isError" -> true
      )
    )

  private def handleToolCallError(id: Option[JsValue], error: McpError): Result =
    if (error.isProtocolError) McpHttpUtil.jsonRpcError(id, error.jsonRpcErrorCode, error.message)
    else buildCallToolErrorResult(id, error)

  private def optionalField(key: String, value: Option[String]): JsObject =
    value.fold(Json.obj())(v => Json.obj(key -> v))
}
