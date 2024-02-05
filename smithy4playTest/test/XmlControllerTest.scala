import de.innfactory.smithy4play.CodecDecider
import de.innfactory.smithy4play.client.GenericAPIClient.EnhancedGenericAPIClient
import de.innfactory.smithy4play.client.SmithyPlayTestUtils._
import models.TestBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import smithy4s.Blob
import smithy4s.http.CaseInsensitive
import testDefinitions.test.{ XmlControllerDefGen, XmlTestInputBody, XmlTestOutput, XmlTestWithInputAndOutputOutput }

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class XmlControllerTest extends TestBase {

  val genericClient = XmlControllerDefGen.withClientAndHeaders(FakeRequestClient, None, List(269))

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "controller.XmlController" must {

    "route to xml test endpoint" in {
      val res = genericClient
        .xmlTestWithInputAndOutput("application/xml", "Concat", XmlTestInputBody("ThisGets", Some(10)))
        .awaitRight
      res.body.body.requiredIntSquared mustBe Some(100)
      res.headers.get(CaseInsensitive("content-type")) mustBe Some(List("application/xml"))
      res.body.body.requiredTestStringConcat mustBe "ThisGetsConcat"
    }

    "route to xml test endpoint with external client" in {
      val concatVal1    = "ConcatThis"
      val concatVal2    = "Test2"
      val squareTest = 3
      val request       = route(
        app,
        FakeRequest("POST", s"/xml/$concatVal2")
          .withHeaders(("content-type", "application/xml"))
          .withXmlBody(
            <XmlTestInputBody>
              <requiredTest>{concatVal1}</requiredTest>
              <requiredInt>
                {squareTest}</requiredInt>
            </XmlTestInputBody>
          )
      ).get
      status(request) mustBe 200
      val resultDecoded = CodecDecider
        .decoder(Seq("application/xml"))
        .fromSchema(XmlTestOutput.schema)
        .decode(Blob(contentAsBytes(request).toArray))
      resultDecoded match {
        case Right(value) =>
          value.requiredTestStringConcat mustBe concatVal1 + concatVal2
          value.requiredIntSquared mustBe Some(squareTest * squareTest)
        case Left(_)      => assert(false)
      }
    }

  }
}
