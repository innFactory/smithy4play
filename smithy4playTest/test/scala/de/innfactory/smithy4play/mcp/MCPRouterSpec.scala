package de.innfactory.smithy4play.mcp

import de.innfactory.smithy4play.mcp.MCPModels._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import testDefinitions.test.MCPExampleControllerService

/**
 * Test suite for MCP functionality
 */
class MCPRouterSpec extends AnyWordSpec with Matchers {

  "MCPModels" should {
    "serialize and deserialize MCPToolDefinition correctly" in {
      val tool = MCPToolDefinition(
        name = "test-tool",
        description = "A test tool",
        inputSchema = Json.obj(
          "type" -> "object",
          "properties" -> Json.obj()
        )
      )

      val json = Json.toJson(tool)
      json.validate[MCPToolDefinition] shouldBe a[JsSuccess[_]]
    }

    "serialize and deserialize MCPCallToolRequest correctly" in {
      val request = MCPCallToolRequest(
        name = "test-tool",
        arguments = Json.obj("param1" -> "value1")
      )

      val json = Json.toJson(request)
      json.validate[MCPCallToolRequest] shouldBe a[JsSuccess[_]]
    }
  }

  "MCPToolDiscovery" should {
    "discover tools from annotated operations" in {
      val discovery = new MCPToolDiscovery()
      val service = MCPExampleControllerService
      
      val tools = discovery.discoverTools(service)
      
      tools should not be empty
      tools.map(_.name) should contain allOf("list_customers", "get_customer", "create_customer", "update_customer", "delete_customer", "search_customers")
    }
  }
}
