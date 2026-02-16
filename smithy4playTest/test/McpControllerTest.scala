import models.TestBase
import play.api.{ Application, Logging }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class McpControllerTest extends TestBase with Logging {

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  private def mcpRequest(body: play.api.libs.json.JsValue): Future[Result] =
    route(
      app,
      FakeRequest("POST", "/mcp")
        .withHeaders("Authorization" -> "Bearer test-token", "content-type" -> "application/json")
        .withJsonBody(body)
    ).get

  "MCP" must {

    "return spec-compliant initialize result with only tools capability" in {
      val initReq = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 0,
        "method"  -> "initialize"
      )

      val future = mcpRequest(initReq)

      status(future) mustBe 200
      val json   = contentAsJson(future)
      val result = (json \ "result").as[JsObject]

      (result \ "protocolVersion").as[String] mustBe "2025-06-18"

      val serverInfo = (result \ "serverInfo").as[JsObject]
      (serverInfo \ "name").as[String] must not be empty
      (serverInfo \ "version").as[String] must not be empty

      val caps = (result \ "capabilities").as[JsObject]
      caps.keys must contain("tools")
      caps.keys must not contain "resources"
      caps.keys must not contain "prompts"
      caps.keys must not contain "sampling"
      caps.keys must not contain "logging"
    }

    "return 202 Accepted for notifications/initialized" in {
      val notifyReq = Json.obj(
        "jsonrpc" -> "2.0",
        "method"  -> "notifications/initialized"
      )

      val future = mcpRequest(notifyReq)
      status(future) mustBe 202
    }

    "expose ReverseString tool via tools/list" in {
      val listReq = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 1,
        "method"  -> "tools/list"
      )

      val future = mcpRequest(listReq)

      status(future) mustBe 200
      val json   = contentAsJson(future)
      val result = (json \ "result").as[JsObject]
      val tools  = (result \ "tools").as[Seq[JsObject]]

      val tool = tools.find(t => (t \ "name").as[String] == "McpControllerService.ReverseString")
      tool mustBe defined
      (tool.get \ "description").as[String] mustBe "Gets MCP data for testing purposes"
      (tool.get \ "inputSchema").asOpt[JsObject] mustBe defined
    }

    "omit description when not present in tool listing" in {
      val listReq = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 1,
        "method"  -> "tools/list"
      )

      val future = mcpRequest(listReq)

      status(future) mustBe 200
      val json   = contentAsJson(future)
      val result = (json \ "result").as[JsObject]
      val tools  = (result \ "tools").as[Seq[JsObject]]

      tools.foreach { tool =>
        val desc = (tool \ "description").asOpt[String]
        desc.foreach(_ must not be empty)
      }
    }

    "call ReverseString tool" in {
      val callReq = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 2,
        "method"  -> "tools/call",
        "params"  -> Json.obj(
          "name"      -> "McpControllerService.ReverseString",
          "arguments" -> Json.obj("text" -> "abcd")
        )
      )

      val future = mcpRequest(callReq)

      status(future) mustBe 200
      val json    = contentAsJson(future)
      val result  = (json \ "result").as[JsObject]
      val content = (result \ "content").as[Seq[JsObject]]
      val text    = (content.head \ "text").as[String]
      text mustBe "{\"reversed\":\"dcba\",\"v\":8}"

      (result \ "isError").asOpt[Boolean] mustBe None
    }

    "return CallToolResult with isError for tool execution errors" in {
      val callReq = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 4,
        "method"  -> "tools/call",
        "params"  -> Json.obj(
          "name"      -> "McpControllerService.ReverseString",
          "arguments" -> Json.obj()
        )
      )

      val future = mcpRequest(callReq)

      status(future) mustBe 200
      val json = contentAsJson(future)

      val resultOpt = (json \ "result").asOpt[JsObject]
      val errorOpt  = (json \ "error").asOpt[JsObject]

      (resultOpt, errorOpt) match {
        case (Some(result), _) =>
          (result \ "isError").as[Boolean] mustBe true
          val content = (result \ "content").as[Seq[JsObject]]
          content must not be empty
        case (_, Some(_))      =>
          succeed
        case _                 =>
          fail("Expected either a result with isError or a JSON-RPC error")
      }
    }

    "return JSON-RPC error for unknown tool name" in {
      val callReq = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 5,
        "method"  -> "tools/call",
        "params"  -> Json.obj(
          "name"      -> "NonExistent.Tool",
          "arguments" -> Json.obj()
        )
      )

      val future = mcpRequest(callReq)

      status(future) mustBe 200
      val json  = contentAsJson(future)
      val error = (json \ "error").as[JsObject]
      (error \ "code").as[Int] mustBe -32602
      (error \ "message").as[String] must include("NonExistent.Tool")
    }

    "reject unauthorized requests" in {
      val listReq = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 3,
        "method"  -> "tools/list"
      )
      val future  = route(
        app,
        FakeRequest("POST", "/mcp")
          .withHeaders("content-type" -> "application/json")
          .withJsonBody(listReq)
      ).get

      status(future) mustBe 401
      val json = contentAsJson(future)
      (json \ "error").as[JsObject].value("code").as[Int] mustBe 401
    }
  }
}
