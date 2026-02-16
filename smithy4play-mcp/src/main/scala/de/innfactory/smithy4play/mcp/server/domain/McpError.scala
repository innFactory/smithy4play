package de.innfactory.smithy4play.mcp.server.domain

sealed trait McpError {
  def message: String
  def jsonRpcErrorCode: Int
  def isProtocolError: Boolean
}

object McpError {

  sealed trait ProtocolError extends McpError {
    override def isProtocolError: Boolean = true
  }

  sealed trait ToolError extends McpError {
    override def isProtocolError: Boolean = false
  }

  final case class ToolNotFound(toolName: String) extends ProtocolError {
    override def message: String       = s"Unknown tool: $toolName"
    override def jsonRpcErrorCode: Int = -32602
  }

  final case class InvalidArguments(toolName: String, reason: String) extends ProtocolError {
    override def message: String       = s"Invalid arguments for tool $toolName: $reason"
    override def jsonRpcErrorCode: Int = -32602
  }

  final case class InvalidJsonDocument(reason: String) extends ProtocolError {
    override def message: String       = s"Invalid JSON document: $reason"
    override def jsonRpcErrorCode: Int = -32600
  }

  final case class MissingHttpHint(toolName: String) extends ToolError {
    override def message: String       = s"Endpoint $toolName missing @http trait"
    override def jsonRpcErrorCode: Int = -32603
  }

  final case class MissingPathParameter(paramName: String) extends ToolError {
    override def message: String       = s"Missing required path parameter: $paramName"
    override def jsonRpcErrorCode: Int = -32602
  }

  final case class NoRouteFound(method: String, path: String) extends ToolError {
    override def message: String       = s"No route found for: $method $path"
    override def jsonRpcErrorCode: Int = -32601
  }

  final case class ToolExecutionError(toolName: String, cause: Throwable) extends ToolError {
    override def message: String       = s"Failed to execute tool $toolName: ${cause.getMessage}"
    override def jsonRpcErrorCode: Int = -32603
  }

  final case class ToolHttpError(toolName: String, statusCode: Int, responseBody: String) extends ToolError {
    override def message: String       = s"Tool $toolName returned HTTP $statusCode: $responseBody"
    override def jsonRpcErrorCode: Int = -32603
  }
}
