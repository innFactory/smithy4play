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
    schema match {
      case s: Schema.StructSchema[I] =>
        generateStructSchema(s)
      case s: Schema.UnionSchema[I] =>
        generateUnionSchema(s)
      case _ =>
        // Fallback for primitive types
        Json.obj(
          "type" -> "object",
          "properties" -> Json.obj(),
          "description" -> "Generic schema"
        )
    }
  }

  /**
   * Generates JSON schema for struct types
   */
  private def generateStructSchema[S](schema: Schema.StructSchema[S]): JsObject = {
    val properties = schema.fields.map { field =>
      field.label -> generateFieldSchema(field)
    }.toMap
    val propertiesJson = JsObject(properties)
    
    val required = schema.fields.collect {
      case field if field.isRequired => field.label
    }
    
    Json.obj(
      "type" -> "object",
      "properties" -> propertiesJson,
      "required" -> JsArray(required.map(JsString).toSeq)
    )
  }

  /**
   * Generates JSON schema for union types  
   */
  private def generateUnionSchema[U](schema: Schema.UnionSchema[U]): JsObject = {
    val alternatives = schema.alternatives.map { alt =>
      Json.obj(
        "type" -> "object",
        "properties" -> Json.obj(
          alt.label -> Json.obj("type" -> "string") // Simplified for alternatives
        ),
        "required" -> Json.arr(alt.label)
      )
    }
    
    Json.obj(
      "oneOf" -> JsArray(alternatives.toSeq)
    )
  }

  /**
   * Generates JSON schema for individual fields
   */
  private def generateFieldSchema[A](field: smithy4s.schema.Field[_, A]): JsValue = {
    field.schema match {
      case _: Schema.PrimitiveSchema[_] =>
        field.schema.toString match {
          case "String" | "string" => Json.obj("type" -> "string")
          case "Integer" | "int" => Json.obj("type" -> "integer")
          case "Long" | "long" => Json.obj("type" -> "integer", "format" -> "int64")
          case "Float" | "float" => Json.obj("type" -> "number", "format" -> "float")
          case "Double" | "double" => Json.obj("type" -> "number", "format" -> "double")
          case "Boolean" | "boolean" => Json.obj("type" -> "boolean")
          case _ => Json.obj("type" -> "string")
        }
      case _: Schema.CollectionSchema[_, _] =>
        Json.obj(
          "type" -> "array",
          "items" -> Json.obj("type" -> "string") // Simplified
        )
      case _: Schema.MapSchema[_, _] =>
        Json.obj(
          "type" -> "object",
          "additionalProperties" -> Json.obj("type" -> "string") // Simplified
        )
      case s: Schema.StructSchema[_] =>
        generateStructSchema(s)
      case _ =>
        Json.obj("type" -> "string") // Fallback
    }
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