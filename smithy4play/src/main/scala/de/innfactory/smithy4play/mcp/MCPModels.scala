package de.innfactory.smithy4play.mcp

import play.api.libs.json._

/**
 * Model Context Protocol data models for tool definitions and responses
 */

case class MCPToolDefinition(
    name: String,
    description: String,
    inputSchema: JsObject
)

case class MCPListToolsResponse(
    tools: Seq[MCPToolDefinition]
)

case class MCPCallToolRequest(
    name: String,
    arguments: JsObject
)

case class MCPCallToolResponse(
    content: Seq[MCPContent],
    isError: Boolean = false
)

case class MCPContent(
    `type`: String,
    text: String
)

object MCPModels {
  implicit val mcpContentFormat: Format[MCPContent] = Json.format[MCPContent]
  implicit val mcpToolDefinitionFormat: Format[MCPToolDefinition] = Json.format[MCPToolDefinition]
  implicit val mcpListToolsResponseFormat: Format[MCPListToolsResponse] = Json.format[MCPListToolsResponse]
  implicit val mcpCallToolRequestFormat: Format[MCPCallToolRequest] = Json.format[MCPCallToolRequest]
  implicit val mcpCallToolResponseFormat: Format[MCPCallToolResponse] = Json.format[MCPCallToolResponse]
}