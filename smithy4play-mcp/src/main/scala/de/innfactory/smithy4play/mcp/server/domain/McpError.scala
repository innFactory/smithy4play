package de.innfactory.smithy4play.mcp.server.domain

sealed trait McpError {
  def message: String
  def jsonRpcErrorCode: Int
}

object McpError {
  final case class ToolNotFound(toolName: String) extends McpError {
    override def message: String       = s"Unknown tool: $toolName"
    override def jsonRpcErrorCode: Int = -32601 // Method not found
  }

  final case class InvalidArguments(toolName: String, reason: String) extends McpError {
    override def message: String       = s"Invalid arguments for tool $toolName: $reason"
    override def jsonRpcErrorCode: Int = -32602 // Invalid params
  }

  final case class MissingHttpHint(toolName: String) extends McpError {
    override def message: String       = s"Endpoint $toolName missing @http trait"
    override def jsonRpcErrorCode: Int = -32603 // Internal error
  }

  final case class MissingPathParameter(paramName: String) extends McpError {
    override def message: String       = s"Missing required path parameter: $paramName"
    override def jsonRpcErrorCode: Int = -32602 // Invalid params
  }

  final case class NoRouteFound(method: String, path: String) extends McpError {
    override def message: String       = s"No route found for: $method $path"
    override def jsonRpcErrorCode: Int = -32601 // Method not found
  }

  final case class ToolExecutionError(toolName: String, cause: Throwable) extends McpError {
    override def message: String       = s"Failed to execute tool $toolName: ${cause.getMessage}"
    override def jsonRpcErrorCode: Int = -32603 // Internal error
  }

  final case class InvalidJsonDocument(reason: String) extends McpError {
    override def message: String       = s"Invalid JSON document: $reason"
    override def jsonRpcErrorCode: Int = -32600 // Invalid Request
  }
}
