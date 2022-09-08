import de.innfactory.smithy4play.client.{RequestClient, SmithyClientResponse}
import de.innfactory.smithy4play.client.SmithyPlayTestUtils._
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.Application
import play.api.Play.materializer
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.{AnyContentAsEmpty,  Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testDefinitions.test.TestRequestBody
import smithy4s.ByteArray

import java.io.File
import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestControllerTest extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory {

  implicit object FakeRequestClient extends RequestClient {
    override def send(
      method: String,
      path: String,
      headers: Map[String, Seq[String]],
      body: Option[Array[Byte]]
    ): Future[SmithyClientResponse] = {
      val baseRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(method, path)
        .withHeaders(headers.toList.flatMap(headers => headers._2.map(v => (headers._1, v))): _*)
      val res                                              =
        if (body.isDefined) route(app, baseRequest.withBody(body.get)).get
        else
          route(
            app,
            baseRequest
          ).get

      for {
        result                <- res
        headers                = result.header.headers.map(v => (v._1, Seq(v._2)))
        body                  <- result.body.consumeData.map(_.toArrayUnsafe())
        bodyConsumed           = if (result.body.isKnownEmpty) None else Some(body)
        contentType            = result.body.contentType
        headersWithContentType =
          if (contentType.isDefined) headers + ("Content-Type" -> Seq(contentType.get)) else headers
      } yield SmithyClientResponse(bodyConsumed, headersWithContentType, result.header.status)

    }
  }

  val smithyTestTest = new SmithyPlayTestClient(None)

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "controller.TestController" must {
    "route to Test Endpoint" in {

      val result = smithyTestTest.test().awaitRight

      result.statusCode mustBe result.expectedStatusCode
    }

    "route to Test Endpoint by SmithyTestClient with Query Parameter, Path Parameter and Body" in {
      val pathParam  = "thisIsAPathParam"
      val testQuery  = "thisIsATestQuery"
      val testHeader = "thisIsATestHeader"
      val body       = TestRequestBody("thisIsARequestBody")
      val result     = smithyTestTest.testWithOutput(pathParam, testQuery, testHeader, body).awaitRight

      val responseBody = result.body.get
      result.statusCode mustBe result.expectedStatusCode
      responseBody.body.testQuery mustBe testQuery
      responseBody.body.pathParam mustBe pathParam
      responseBody.body.bodyMessage mustBe body.message
      responseBody.body.testHeader mustBe testHeader
    }

    "route to Test Endpoint but should return error because required header is not set" in {
      val pathParam                                 = "thisIsAPathParam"
      val testQuery                                 = "thisIsATestQuery"
      val testHeader                                = "thisIsATestHeader"
      val body                                      = TestRequestBody("thisIsARequestBody")
      implicit val format: OWrites[TestRequestBody] = Json.writes[TestRequestBody]
      val future: Future[Result]                    =
        route(
          app,
          FakeRequest("POST", s"/test/$pathParam?testQuery=$testQuery")
            .withHeaders(("Wrong-Header", testHeader))
            .withBody(Json.toJson(body))
        ).get

      status(future) mustBe 500
    }

    "route to Query Endpoint but should return error because query is not set" in {
      val testQuery                                 = "thisIsATestQuery"
      implicit val format: OWrites[TestRequestBody] = Json.writes[TestRequestBody]
      val future: Future[Result]                    =
        route(
          app,
          FakeRequest("GET", s"/query?WrongQuery=$testQuery")
        ).get

      status(future) mustBe 500
    }

    "route to Health Endpoint" in {
      val result = smithyTestTest.health().awaitRight

      result.statusCode mustBe result.expectedStatusCode
    }

    "route to Blob Endpoint" in {
      val path       = getClass.getResource("/testPicture.png").getPath
      val file       = new File(path)
      val pngAsBytes = ByteArray(Files.readAllBytes(file.toPath))
      val result     = smithyTestTest.testWithBlob(pngAsBytes, "image/png").awaitRight

      result.statusCode mustBe result.expectedStatusCode
    }
  }
}