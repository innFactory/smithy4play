package de.innfactory.smithy4play.mcp

import cats.data.EitherT
import cats.implicits.*
import com.google.inject.{Inject, Singleton}
import de.innfactory.smithy4play.mcp.auth.{AuthError, AuthService, JWToken}
import de.innfactory.smithy4play.mcp.session.{ClientInfo, SessionManager}
import io.cleverone.mcp.server.domain.{McpError, Tool}
import io.cleverone.mcp.server.service.McpToolRegistryService
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.*
import play.api.mvc.*
import smithy4s.Document

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class McpController @Inject()(
    mcpToolRegistry: McpToolRegistryService,
    authService: AuthService,
    sessionManager: SessionManager,
    cc: ControllerComponents,
    actorSystem: ActorSystem,
    lifecycle: ApplicationLifecycle
)(using ExecutionContext, Materializer)
    extends AbstractController(cc) {

  private val SERVER_NAME = "smithy4play-mcp-server"
  private val SERVER_VERSION = "1.0.0"
  private val PROTOCOL_VERSION = "2024-11-05"

  private val cleanupScheduler = actorSystem.scheduler.scheduleAtFixedRate(
    initialDelay = 10.minutes,
    interval = 10.minutes
  )(() => sessionManager.cleanupOldSessions())

  lifecycle.addStopHook { () =>
    Future {
      cleanupScheduler.cancel()
      println(s"[MCP] Shutdown - cleaned up active sessions")
    }
  }

  private final case class JsonRpcRequest(
      jsonrpcVersion: Option[String],
      id: Option[JsValue],
      method: Option[String],
      params: Option[JsValue]
  )

  def optionsCors(): Action[AnyContent] = Action { implicit request =>
    addCorsAndStreamingHeaders(NoContent)
  }

  def jsonRpcEndpoint(): Action[JsValue] = Action.async(parse.json(maxLength = 10 * 1024 * 1024)) { implicit request =>
    val jsonRpcRequest = parseJsonRpcRequest(request.body)
    val sessionId = request.headers.get("X-Session-ID")
    val authorization = request.headers.get("Authorization")

    println(s"[MCP JSON-RPC] Request: method=${jsonRpcRequest.method}, id=${jsonRpcRequest.id}, session=$sessionId")

    val result = for {
      token <- extractTokenFromAuthHeader(authorization)
      _ <- validateJwtToken(token)
      _ = println(token)
      response <- EitherT.right[String](handleJsonRpcRequest(jsonRpcRequest, sessionId, request))
    } yield response

    result.fold(
      error => buildJsonRpcError(jsonRpcRequest.id, 401, s"Unauthorized: $error"),
      identity
    )
  }

  private def parseJsonRpcRequest(body: JsValue): JsonRpcRequest =
    JsonRpcRequest(
      jsonrpcVersion = (body \ "jsonrpc").asOpt[String],
      id = (body \ "id").toOption,
      method = (body \ "method").asOpt[String],
      params = (body \ "params").toOption
    )

  private def extractTokenFromAuthHeader(authHeader: Option[String]): EitherT[Future, String, JWToken] =
    authHeader match {
      case None => EitherT.leftT("Missing Authorization header")
      case Some(header) =>
        val tokenOpt = header.stripPrefix("Bearer ").stripPrefix("bearer ")
        if (tokenOpt.isEmpty || tokenOpt == header) {
          EitherT.leftT("Invalid Authorization header format. Expected 'Bearer <token>'")
        } else {
          EitherT.rightT(JWToken(tokenOpt))
        }
    }

  private def validateJwtToken(token: JWToken): EitherT[Future, String, Unit] =
    authService.verifyAwsToken(token).leftMap { error =>
      error match {
        case AuthError.InvalidToken(reason)       => s"Invalid token: $reason"
        case AuthError.TokenExpired(reason)       => s"Token expired: $reason"
        case AuthError.UnauthorizedAccess(reason) => s"Unauthorized: $reason"
      }
    }.map(_ => ())

  private def handleJsonRpcRequest(
      req: JsonRpcRequest,
      sessionId: Option[String],
      request: play.api.mvc.Request[?]
  ): Future[Result] =
    if (!req.jsonrpcVersion.contains("2.0"))
      Future.successful(buildJsonRpcError(req.id, -32600, "Invalid Request: jsonrpc must be 2.0"))
    else
      req.method match {
        case Some("initialize")                => handleInitializeRequest(req.id, req.params, sessionId)
        case Some("notifications/initialized") => handleInitializedNotification(req.id, sessionId)
        case Some("tools/list")                => handleToolsList(req.id, req.params, sessionId)
        case Some("tools/call")                => handleToolsCall(req.id, req.params, sessionId, request)
        case Some("prompts/list")              => handlePromptsList(req.id, req.params, sessionId)
        case Some("prompts/get")               => handlePromptsGet(req.id, req.params, sessionId)
        case Some("resources/list")            => handleResourcesList(req.id, req.params, sessionId)
        case Some("resources/read")            => handleResourcesRead(req.id, req.params, sessionId)
        case Some("ping")                      => Future.successful(buildJsonRpcSuccess(req.id, Json.obj()))
        case Some(m) => Future.successful(buildJsonRpcError(req.id, -32601, s"Method not found: $m"))
        case None    => Future.successful(buildJsonRpcError(req.id, -32600, "Invalid Request: missing method"))
      }

  private def handleInitializeRequest(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] = {
    val clientInfoOpt = extractClientInfo(params)
    updateSessionClientInfo(sessionId, clientInfoOpt)
    clientInfoOpt.foreach(ci => sessionManager.updateSessionClientInfo(newSessionId, ci))

  private def handleInitializeRequest(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] = {
    val newSessionId = sessionId.getOrElse(s"session-${System.nanoTime()}")
    val session = sessionManager.getOrCreateSession(Some(newSessionId))

    val clientInfoOpt = extractClientInfo(params)
    clientInfoOpt.foreach(ci => sessionManager.updateSessionClientInfo(newSessionId, ci))

    Future.successful(
      buildJsonRpcSuccess(
        id,
        Json.obj(
          "protocolVersion" -> PROTOCOL_VERSION,
          "capabilities" -> Json.obj(
            "prompts" -> Json.obj("listChanged" -> false),
            "resources" -> Json.obj(
              "subscribe" -> false,
              "listChanged" -> false
            ),
            "tools" -> Json.obj("listChanged" -> false)
          ),
          "serverInfo" -> Json.obj(
            "name" -> SERVER_NAME,
            "version" -> SERVER_VERSION
          )
        )
      ).withHeaders("X-Session-ID" -> newSessionId)
    )
  }

  private def extractClientInfo(params: Option[JsValue]): Option[ClientInfo] =
    params.flatMap { p =>
      (p \ "clientInfo").asOpt[JsValue].flatMap { ci =>
        for {
          name <- (ci \ "name").asOpt[String]
          version <- (ci \ "version").asOpt[String]
        } yield ClientInfo(name = name, version = version)
      }
    }

  private def handleInitializedNotification(
      id: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] = {
    sessionId.foreach(sid => sessionManager.markSessionInitialized(sid))
    Future.successful(buildJsonRpcSuccess(id, JsNull))
  }

  private def handleToolsList(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] = {
    val tools = mcpToolRegistry.getAllTools
    val toolsJson = tools.map(toolToJson)

    println(s"[MCP] Reporting ${tools.size} available tools")
    tools.headOption.foreach(tool => println(s"[MCP] Tools: ${documentToJsValue(tool.inputSchema)}"))

    Future.successful(
      buildJsonRpcSuccess(
        id,
        Json.obj("tools" -> toolsJson)
      )
    )
  }

  private def toolToJson(tool: Tool): JsObject =
    Json.obj(
      "name" -> tool.name,
      "description" -> tool.description.getOrElse(""),
      "inputSchema" -> documentToJsValue(tool.inputSchema)
    )

  private def documentToJsValue(document: Document): JsValue =
    document match {
      case Document.DNumber(value)  => Json.toJson(value)
      case Document.DString(value)  => Json.toJson(value)
      case Document.DBoolean(value) => Json.toJson(value)
      case Document.DNull           => JsNull
      case Document.DArray(value)   => Json.toJson(value.map(documentToJsValue))
      case Document.DObject(value)  => Json.toJson(value.map(t => (t._1, documentToJsValue(t._2))))
    }

  private def jsValueToSmithyDocument(jsValue: JsValue): Document = {
    jsValue match {
      case JsNull       => Document.DNull
      case JsBoolean(b) => Document.DBoolean(b)
      case JsNumber(n) =>
        if (n.isValidInt) Document.DNumber(n.toInt)
        else if (n.isValidLong) Document.DNumber(n.toLong)
        else Document.DNumber(n.toDouble)
      case JsString(s)   => Document.DString(s)
      case JsArray(arr)  => Document.DArray(arr.map(jsValueToSmithyDocument).toIndexedSeq)
      case obj: JsObject => Document.DObject(obj.value.view.mapValues(jsValueToSmithyDocument).toMap)
    }
  }

  private def handleToolsCall(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String],
      request: play.api.mvc.Request[?]
  ): Future[Result] = {
    val result = for {
      toolName <- EitherT.fromOption[Future](
        params.flatMap(p => (p \ "name").asOpt[String]),
        "Missing tool name"
      )
      arguments = params.flatMap(p => (p \ "arguments").toOption)
      smithyArgs = arguments.map(jsValueToSmithyDocument)
      response <- mcpToolRegistry.callTool(toolName, smithyArgs, request).leftMap(_.message)
    } yield buildToolCallSuccessResponse(id, response)

    result.fold(
      error => buildToolCallErrorResponse(id, error),
      identity
    )
  }

  private def buildToolCallSuccessResponse(id: Option[JsValue], result: String): Result =
    buildJsonRpcSuccess(
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
    buildJsonRpcSuccess(
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

  private def handlePromptsList(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] =
    Future.successful(
      buildJsonRpcSuccess(
        id,
        Json.obj("prompts" -> Json.arr())
      )
    )

  private def handlePromptsGet(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] =
    Future.successful(
      buildJsonRpcSuccess(
        id,
        Json.obj("messages" -> Json.arr())
      )
    )

  private def handleResourcesList(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] =
    Future.successful(
      buildJsonRpcSuccess(
        id,
        Json.obj("resources" -> Json.arr())
      )
    )

  private def handleResourcesRead(
      id: Option[JsValue],
      params: Option[JsValue],
      sessionId: Option[String]
  ): Future[Result] =
    Future.successful(
      buildJsonRpcSuccess(
        id,
        Json.obj("contents" -> Json.arr())
      )
    )

  private def addCorsAndStreamingHeaders(result: Result): Result =
    result.withHeaders(
      "Access-Control-Allow-Origin" -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers" -> "Content-Type, Authorization, X-Session-ID",
      "Access-Control-Max-Age" -> "3600",
      "Access-Control-Expose-Headers" -> "X-Session-ID",
      "Cache-Control" -> "no-cache, no-store, must-revalidate",
      "Connection" -> "keep-alive",
      "X-Content-Type-Options" -> "nosniff",
      "X-Accel-Buffering" -> "no"
    )

  private def buildJsonRpcSuccess(id: Option[JsValue], result: JsValue): Result =
    addCorsAndStreamingHeaders(
      Ok(
        Json.obj(
          "jsonrpc" -> "2.0",
          "id" -> id.getOrElse(JsNull),
          "result" -> result
        )
      )
    )

  private def buildJsonRpcError(id: Option[JsValue], code: Int, message: String): Result =
    addCorsAndStreamingHeaders(
      Ok(
        Json.obj(
          "jsonrpc" -> "2.0",
          "id" -> id.getOrElse(JsNull),
          "error" -> Json.obj(
            "code" -> code,
            "message" -> message
          )
        )
      )
    )
}

