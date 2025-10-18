package de.innfactory.smithy4play.mcp

import de.innfactory.smithy4play.mcp.MCPModels._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test._

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

    "serialize and deserialize MCPCallToolResponse correctly" in {
      val response = MCPCallToolResponse(
        content = Seq(MCPContent("text", "Test response")),
        isError = false
      )

      val json = Json.toJson(response)
      json.validate[MCPCallToolResponse] shouldBe a[JsSuccess[_]]
    }
  }

  "MCPToolDiscovery" should {
    "discover tools from annotated operations" in {
      // This is a placeholder test - in a full implementation,
      // we would create a mock service with MCP-annotated operations
      val discovery = new MCPToolDiscovery()
      // discovery.discoverTools(...) should return the expected tools
      succeed
    }
  }
}