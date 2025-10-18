package de.innfactory.smithy4play.mcp

import de.innfactory.mcp.{McpCategories, McpDescription, McpName, McpTool}
import play.api.libs.json._
import smithy4s.schema.Schema

/**
 * Service for discovering MCP-annotated operations and converting them to tool definitions
 */
class MCPToolDiscovery {

  /**
   * Discovers all MCP-annotated operations from a service and converts them to tool definitions
   */
  def discoverTools[Alg[_[_, _, _, _, _]]](service: smithy4s.Service[Alg]): Seq[MCPToolDefinition] = {
    service.endpoints.flatMap { endpoint =>
      val hints = endpoint.hints
      
      // Check if the endpoint has the mcpTool trait
      hints.get(McpTool).map { _ =>
        val toolName = hints.get(McpName)
          .map(_.value)
          .getOrElse(endpoint.id.name)
        
        val description = hints.get(McpDescription)
          .map(_.value)
          .getOrElse(s"Tool for ${endpoint.id.name}")
        
        val inputSchema = generateInputSchema(endpoint.input)
        
        MCPToolDefinition(
          name = toolName,
          description = description,
          inputSchema = inputSchema
        )
      }
    }
  }

  /**
   * Generates JSON schema for the input type of an endpoint
   */
  private def generateInputSchema[I](schema: Schema[I]): JsObject = {
    // For now, generate a basic schema structure
    // In a full implementation, this would use smithy4s schema introspection
    // to generate proper JSON schema
    Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(),
      "required" -> Json.arr()
    )
  }

  /**
   * Extracts categories from MCP hints, defaulting to service name
   */
  private def extractCategories(hints: smithy4s.Hints, serviceName: String): Seq[String] = {
    hints.get(McpCategories)
      .map(_.value)
      .getOrElse(Seq(serviceName))
  }
}