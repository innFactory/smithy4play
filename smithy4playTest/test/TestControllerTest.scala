import com.typesafe.config.Config
import de.innfactory.smithy4play.AutoRouter
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, OWrites, Reads}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import smithy4s.ByteArray
import testDefinitions.test.{BlobResponse, TestRequestBody, TestResponseBody}

import java.nio.file.{Files, Paths}
import java.io.File
import scala.concurrent.{ExecutionContext, Future}

class TestControllerTest extends PlaySpec
with BaseOneAppPerSuite
  with FakeApplicationFactory
 {

   override def fakeApplication(): Application =
     new GuiceApplicationBuilder().build()

   "controller.TestController" must {
     "route to Test Endpoint" in {
       val future: Future[Result] =
         route(
           app,
           FakeRequest("GET", "/")
         ).get

       val res    = contentAsJson(future)
       println(res)
       status(future) mustBe 200
     }

     "route to Test Endpoint with Query Parameter, Path Parameter and Body" in {
       val pathParam = "thisIsAPathParam"
       val testQuery = "thisIsATestQuery"
       val testHeader = "thisIsATestHeader"
       val body = TestRequestBody("thisIsARequestBody")
       implicit val writes: OWrites[TestRequestBody] = Json.writes[TestRequestBody]

       val future: Future[Result] =
         route(
           app,
           FakeRequest("POST", s"/test/$pathParam?testQuery=$testQuery")
             .withHeaders(("Test-Header", testHeader ))
             .withBody(Json.toJson(body))
         ).get

       implicit val reads: Reads[TestResponseBody] = Json.reads[TestResponseBody]
       val res    = contentAsJson(future).as[TestResponseBody]
       status(future) mustBe 200
       res.testQuery mustBe testQuery
       res.pathParam mustBe pathParam
       res.bodyMessage mustBe body.message
       res.testHeader mustBe testHeader
     }

     "route to Test Endpoint but should return error because required header is not set" in {
       val pathParam = "thisIsAPathParam"
       val testQuery = "thisIsATestQuery"
       val testHeader = "thisIsATestHeader"
       val body = TestRequestBody("thisIsARequestBody")
       implicit val format: OWrites[TestRequestBody] = Json.writes[TestRequestBody]
       val future: Future[Result] =
         route(
           app,
           FakeRequest("POST", s"/test/$pathParam?testQuery=$testQuery")
             .withHeaders(("Wrong-Header", testHeader ))
             .withBody(Json.toJson(body))
         ).get

       status(future) mustBe 500
     }

     "route to Health Endpoint" in {
       val future: Future[Result] =
         route(
           app,
           FakeRequest("GET", s"/health")
         ).get

       status(future) mustBe 200
     }

     "route to Blob Endpoint" in {
       val path = getClass.getResource("/testPicture.png").getPath
       val file = new File(path)
       val pngAsBytes = Files.readAllBytes(file.toPath)
       val future: Future[Result] =
         route(
           app,
           FakeRequest("POST", s"/blob").withHeaders(("Content-Type", "image/png")).withBody(pngAsBytes)
         ).get

       status(future) mustBe 200
     }
   }
 }

