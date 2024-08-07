import controller.models.TestError
import models.NodeImplicits.NodeEnhancer
import models.{ TestBase, TestJson }
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Json, OWrites }
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import smithy4s.{ Blob, Document }
import smithy4s.http.CaseInsensitive
import testDefinitions.test.{
  JsonInput,
  SimpleTestResponse,
  TestControllerServiceGen,
  TestRequestBody,
  TestResponseBody,
  TestWithOutputResponse
}

import java.io.File
import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestControllerTest extends TestBase {

  val genericClient = client(TestControllerServiceGen.service)

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "controller.TestController" must {

//    "new autoTest test" in {
//      new ComplianceClient(genericClient).tests().map { result =>
//        200 mustBe result.receivedCode
//        result.expectedBody mustBe result.receivedBody
//      }
//    }
//
//    "autoTest 500" in {
//      new ComplianceClient(genericClient).tests(Some("500")).map { result =>
//        result.expectedCode must not be result.receivedCode
//        result.receivedError mustBe result.expectedError
//      }
//    }
//
//    "route to Test Endpoint" in {
//      val result = genericClient.test().awaitRight
//      result.statusCode mustBe 200
//    }
//
//    "route to Test Endpoint by SmithyTestClient with Query Parameter, Path Parameter and Body" in {
//      val pathParam  = "thisIsAPathParam"
//      val testQuery  = "thisIsATestQuery"
//      val testHeader = "thisIsATestHeader"
//      val body       = TestRequestBody("thisIsARequestBody")
//      val result     = genericClient.testWithOutput(pathParam, testQuery, testHeader, body).awaitRight
//
//      val responseBody = result.body
//      result.statusCode mustBe 200
//      responseBody.body.testQuery mustBe testQuery
//      responseBody.body.pathParam mustBe pathParam
//      responseBody.body.bodyMessage mustBe body.message
//      responseBody.body.testHeader mustBe testHeader
//    }

    "route to Test Endpoint with Query Parameter, Path Parameter and Body with fake request" in {
      val pathParam                                 = "thisIsAPathParam"
      val testQuery                                 = "thisIsATestQuery"
      val testHeader                                = "thisIsATestHeader"
      val body                                      = TestRequestBody("thisIsARequestBody")
      implicit val format: OWrites[TestRequestBody] = Json.writes[TestRequestBody]
      val future: Future[Result]                    =
        route(
          app,
          FakeRequest("POST", s"/test/$pathParam?testQuery=$testQuery")
            .withHeaders(("Test-Header", testHeader))
            .withBody(Json.toJson(body))
        ).get

      implicit val formatBody = Json.format[TestResponseBody]
      val responseBody        = contentAsJson(future).as[TestResponseBody]
      status(future) mustBe 200
      responseBody.testQuery mustBe testQuery
      responseBody.pathParam mustBe pathParam
      responseBody.bodyMessage mustBe body.message
      responseBody.testHeader mustBe testHeader
    }

    "route to Test Endpoint with Query Parameter, Path Parameter and Body with fake request and xml protocol" in {
      val pathParam              = "thisIsAPathParam"
      val testQuery              = "thisIsATestQuery"
      val testHeader             = "thisIsATestHeader"
      val testBody               = "thisIsARequestBody"
      val xml                    = <TestRequestBody><message>{testBody}</message></TestRequestBody>
      val future: Future[Result] =
        route(
          app,
          FakeRequest("POST", s"/test/$pathParam?testQuery=$testQuery")
            .withHeaders(("Test-Header", testHeader))
            .withXmlBody(xml)
        ).get
      status(future) mustBe 200
      val xmlRes                 = scala.xml.XML.loadString(contentAsString(future))
      xmlRes.normalize mustBe <TestResponseBody>
      <testHeader>{testHeader}</testHeader>
        <pathParam>{pathParam}</pathParam>
        <testQuery>{testQuery}</testQuery>
        <bodyMessage>{testBody}</bodyMessage>
      </TestResponseBody>.normalize
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

      status(future) mustBe 400
    }

    "route to Query Endpoint but should return error because query is not set" in {
      val testQuery              = "thisIsATestQuery"
      val future: Future[Result] =
        route(
          app,
          FakeRequest("GET", s"/query?WrongQuery=$testQuery")
        ).get

      status(future) mustBe 400
    }

//    "route to Health Endpoint" in {
//      val result = genericClient.health().run(null)
//
//      result.headers.contains(CaseInsensitive("endpointresulttest")) mustBe true
//      result.statusCode mustBe 200
//    }
//
//    "route to error Endpoint" in {
//      val result = genericClient.testThatReturnsError().awaitLeft
//
//      result.toErrorResponse[TestError].message must include("fail")
//      result.statusCode mustBe 500
//    }
//
//    "route to Blob Endpoint" in {
//      val path       = getClass.getResource("/testPicture.png").getPath
//      val file       = new File(path)
//      val pngAsBytes = Blob(Files.readAllBytes(file.toPath))
//      val result     = genericClient.testWithBlob(pngAsBytes, "image/png").awaitRight(global, 5.hours)
//
//      result.statusCode mustBe 200
//      pngAsBytes mustBe result.body.body
//    }
//
//    "route with json body to Blob Endpoint" in {
//      val testString = "StringToBeParsedCorrectly"
//      val result     = genericClient.testWithJsonInputAndBlobOutput(JsonInput(testString)).awaitRight(global, 5.hours)
//
//      result.statusCode mustBe 200
//      testString mustBe result.body.body.toUTF8String
//    }
//
//    "route to Auth Test" in {
//      val result = genericClient.testAuth().awaitLeft
//
//      result.statusCode mustBe 401
//    }
//
//    "test with different status code" in {
//      val result = genericClient.testWithOtherStatusCode().awaitRight
//
//      result.statusCode mustBe 269
//    }
//
//    "manual writing json" in {
//
//      val writtenData = smithy4s.json.Json.writeBlob(SimpleTestResponse(Some("Test")))
//
//      val writtenJson = Json.parse(writtenData.toArray).as[TestJson]
//
//      val readData =
//        smithy4s.json.Json.read(Blob(Json.toBytes(Json.toJson(TestJson(Some("Test"))))))(SimpleTestResponse.schema)
//
//      writtenJson.message mustBe Some("Test")
//      readData match {
//        case Right(value) => value.message mustBe Some("Test")
//        case _            => fail("should parse")
//      }
//    }
  }
}
