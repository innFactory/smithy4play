import controller.models.TestError
import de.innfactory.smithy4play.CodecUtils
import de.innfactory.smithy4play.client.GenericAPIClient.EnhancedGenericAPIClient
import de.innfactory.smithy4play.client.{ RequestClient, SmithyClientResponse }
import de.innfactory.smithy4play.client.SmithyPlayTestUtils._
import de.innfactory.smithy4play.compliancetests.ComplianceClient
import models.TestJson
import org.scalatestplus.play.{ BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec }
import play.api.Application
import play.api.Play.materializer
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Json, OWrites }
import play.api.mvc.{ AnyContentAsEmpty, Result }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testDefinitions.test.{ SimpleTestResponse, TestControllerServiceGen, TestRequestBody }
import smithy4s.ByteArray

import java.io.File
import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

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

  val genericClient = TestControllerServiceGen.withClientAndHeaders(FakeRequestClient, None, List(269))

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "controller.TestController" must {

    "new autoTest test" in {
      new ComplianceClient(genericClient).tests().map { result =>
        200 mustBe result.receivedCode
        result.expectedBody mustBe result.receivedBody
      }
    }

    "autoTest 500" in {
      new ComplianceClient(genericClient).tests(Some("500")).map { result =>
        result.expectedCode must not be result.receivedCode
        result.receivedError mustBe result.expectedError
      }
    }

    "route to Test Endpoint" in {
      val result = genericClient.test().awaitRight
      result.statusCode mustBe 200
    }

    "route to Test Endpoint by SmithyTestClient with Query Parameter, Path Parameter and Body" in {
      val pathParam  = "thisIsAPathParam"
      val testQuery  = "thisIsATestQuery"
      val testHeader = "thisIsATestHeader"
      val body       = TestRequestBody("thisIsARequestBody")
      val result     = genericClient.testWithOutput(pathParam, testQuery, testHeader, body).awaitRight

      val responseBody = result.body.get
      result.statusCode mustBe 200
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
      val result = genericClient.health().awaitRight

      result.headers.contains("endpointresulttest") mustBe true
      result.statusCode mustBe 200
    }

    "route to error Endpoint" in {
      val result = genericClient.testThatReturnsError().awaitLeft

      result.toErrorResponse[TestError].message must include("fail")
      result.statusCode mustBe 500
    }

    "route to Blob Endpoint" in {
      val path       = getClass.getResource("/testPicture.png").getPath
      val file       = new File(path)
      val pngAsBytes = ByteArray(Files.readAllBytes(file.toPath))
      val result     = genericClient.testWithBlob(pngAsBytes, "image/png").awaitRight

      result.statusCode mustBe 200
      pngAsBytes mustBe result.body.get.body
    }

    "route to Auth Test" in {
      val result = genericClient.testAuth().awaitLeft

      result.statusCode mustBe 401
    }

    "test with different status code" in {
      val result = genericClient.testWithOtherStatusCode().awaitRight

      result.statusCode mustBe 269
    }

    "manual writing json" in {

      val writtenData = CodecUtils.writeEntityToJsonBytes(SimpleTestResponse(Some("Test")), SimpleTestResponse.schema)

      val writtenJson = Json.parse(writtenData).as[TestJson]

      val readData = CodecUtils.readFromJsonBytes(
        Json.toBytes(Json.toJson(TestJson(Some("Test")))),
        SimpleTestResponse.schema
      )

      writtenJson.message mustBe Some("Test")
      readData.get.message mustBe Some("Test")
    }
  }
}
