import models.TestBase
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class McpControllerTest extends TestBase:

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  "MCP" must {

    "expose ReverseString tool via tools/list" in {
      val listReq                = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 1,
        "method"  -> "tools/list"
      )
      val future: Future[Result] = route(
        app,
        FakeRequest("POST", "/mcp")
          .withHeaders("Authorization" -> "Bearer test-token", "content-type" -> "application/json")
          .withJsonBody(listReq)
      ).get

      status(future) mustBe 200
      val json   = contentAsJson(future)
      val result = (json \ "result").get
      val tools  = (result \ "tools").as[Seq[play.api.libs.json.JsObject]]
      tools.head.value("name").as[String] mustBe "McpControllerService.ReverseString"
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

      val future: Future[Result] = route(
        app,
        FakeRequest("POST", "/mcp")
          .withHeaders("Authorization" -> "Bearer test-token", "content-type" -> "application/json")
          .withJsonBody(callReq)
      ).get

      status(future) mustBe 200
      val json    = contentAsJson(future)
      val result  = (json \ "result").get
      val content = (result \ "content").as[Seq[play.api.libs.json.JsObject]]
      val text    = content.head.value("text").as[String]
      text mustBe "{\"reversed\":\"dcba\"}"
    }

    "reject unauthorized requests" in {
      val listReq                = Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> 3,
        "method"  -> "tools/list"
      )
      val future: Future[Result] = route(
        app,
        FakeRequest("POST", "/mcp")
          .withHeaders("content-type" -> "application/json")
          .withJsonBody(listReq)
      ).get

      status(future) mustBe 200
      val json = contentAsJson(future)
      (json \ "error").as[play.api.libs.json.JsObject].value("code").as[Int] mustBe 401
    }
  }
