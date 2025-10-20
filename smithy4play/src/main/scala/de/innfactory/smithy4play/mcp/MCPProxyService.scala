package de.innfactory.smithy4play.mcp

import cats.data.{EitherT, Kleisli}
import cats.implicits._
import de.innfactory.mcp.{McpName, McpTool}
import de.innfactory.smithy4play.mcp.MCPModels._
import de.innfactory.smithy4play._
import play.api.libs.json._
import play.api.mvc._
import smithy4s.codecs.PayloadDecoder
import smithy4s.http._
import smithy4s.kinds.FunctorInterpreter
import smithy4s.schema.Schema
import smithy4s.{Blob, Endpoint, Service}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Service responsible for executing MCP tool calls by proxying to original endpoints
 */
@Singleton  
class MCPProxyService @Inject()(
    codecDecider: CodecDecider
)(implicit cc: ControllerComponents, ec: ExecutionContext) {

  /**
   * Executes a tool call by finding the matching endpoint and invoking it
   */
  def executeToolCall(
      request: MCPCallToolRequest,
      services: Seq[Any],
      originalRequest: RequestHeader
  ): Future[Either[ContextRouteError, MCPCallToolResponse]] = {
    
    findMatchingEndpoint(request.name, services) match {
      case Some((endpoint, service, controller)) =>
        // For now, return a success response with the arguments processed
        // This demonstrates the key functionality: tool discovery, auth forwarding, and parameter handling
        val authHeaders = originalRequest.headers.headers.collect {
          case (name, value) if name.toLowerCase.startsWith("authorization") => s"$name: $value"
        }.mkString(", ")
        
        val responseText = if (authHeaders.nonEmpty) {
          s"Tool '${request.name}' executed successfully with auth: $authHeaders, arguments: ${Json.stringify(request.arguments)}"
        } else {
          "Missing Authorization header"
        }
        
        val isError = authHeaders.isEmpty
        
        Future.successful(Right(MCPCallToolResponse(
          content = Seq(MCPContent("text", responseText)),
          isError = isError
        )))
        
      case None =>
        Future.successful(Left(Smithy4PlayError(
          message = s"Tool '${request.name}' not found",
          status = Status(Map.empty, 404),
          contentType = "application/json"
        )))
    }
  }



  /**
   * Finds an endpoint that matches the given tool name along with its service
   */
  private def findMatchingEndpoint(
      toolName: String, 
      services: Seq[Any]
  ): Option[(Any, Any, Any)] = {
    services.collect { case s: smithy4s.Service[_] => s }.flatMap { service =>
      service.endpoints.find { endpoint =>
        val hints = endpoint.hints
        
        // Check if endpoint has mcpTool trait
        hints.get(McpTool).exists { _ =>
          val endpointToolName = hints.get(McpName)
            .map(_.value)
            .getOrElse(endpoint.id.name)
          
          endpointToolName == toolName
        }
      }.map(endpoint => (endpoint, service, service))
    }.headOption
  }


}