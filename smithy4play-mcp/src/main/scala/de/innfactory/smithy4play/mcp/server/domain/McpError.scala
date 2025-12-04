package de.innfactory.smithy4play.mcp.server.domain

sealed trait McpError {
  def message: String
}

object McpError {
  final case class ToolNotFound(toolName: String) extends McpError {
    override def message: String = s"Unknown tool: $toolName"
  }

  final case class InvalidArguments(toolName: String, reason: String) extends McpError {
    override def message: String = s"Invalid arguments for tool $toolName: $reason"
  }

  final case class MissingHttpHint(toolName: String) extends McpError {
    override def message: String = s"Endpoint $toolName missing @http trait"
  }

  final case class UnauthorizedAccess(resource: String) extends McpError {
    override def message: String = s"Unauthorized access to resource: $resource"
  }

  final case class MissingPathParameter(paramName: String) extends McpError {
    override def message: String = s"Missing required path parameter: $paramName"
  }

  final case class NoRouteFound(method: String, path: String) extends McpError {
    override def message: String = s"No route found for: $method $path"
  }

  final case class ToolExecutionError(toolName: String, cause: Throwable) extends McpError {
    override def message: String = s"Failed to execute tool $toolName: ${cause.getMessage}"
  }

  final case class InvalidJsonDocument(reason: String) extends McpError {
    override def message: String = s"Invalid JSON document: $reason"
  }

  final case class SchemaExtractionError(reason: String) extends McpError {
    override def message: String = s"Failed to extract schema: $reason"
  }

  final case class DocumentTransformationError(reason: String) extends McpError {
    override def message: String = s"Failed to transform document: $reason"
  }
}
