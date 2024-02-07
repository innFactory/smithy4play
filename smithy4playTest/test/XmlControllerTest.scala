import de.innfactory.smithy4play.CodecDecider
import de.innfactory.smithy4play.client.GenericAPIClient.EnhancedGenericAPIClient
import de.innfactory.smithy4play.client.SmithyPlayTestUtils._
import models.NodeImplicits.NodeEnhancer
import models.TestBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json, OFormat }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import smithy4s.Blob
import smithy4s.http.CaseInsensitive
import testDefinitions.test.{ Pouebergabe, XmlControllerDefGen, XmlTestOutput }

import scala.xml._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.{ Elem, Node, PrettyPrinter }

class XmlControllerTest extends TestBase {

  val genericClient = XmlControllerDefGen.withClientAndHeaders(FakeRequestClient, None, List(269))

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "controller.XmlController" must {

    "route to xml test endpoint" in {
      val res = genericClient
        .xmlTestWithInputAndOutput(
          "Concat",
          Pouebergabe("05.02.2024", "ThisGets", Some(10))
        )
        .awaitRight
      res.body.body.requiredIntSquared mustBe Some(100)
      res.headers.get(CaseInsensitive("content-type")) mustBe Some(List("application/xml"))
      res.body.body.requiredTestStringConcat mustBe "ThisGetsConcat"
    }

    "route to xml test endpoint with external client" in {
      val concatVal1 = "ConcatThis"
      val concatVal2 = "Test2"
      val squareTest = 3
      val xml        =
        <pouebergabe serverzeit="05.02.2024">
          <requiredTest>{concatVal1}</requiredTest>
          <requiredInt>{squareTest}</requiredInt>
        </pouebergabe>
      val request    = route(
        app,
        FakeRequest("POST", s"/xml/$concatVal2")
          .withHeaders(("content-type", "application/xml"))
          .withXmlBody(
            xml
          )
      ).get
      status(request) mustBe 200

      val result = scala.xml.XML.loadString(contentAsString(request))
      result.normalize mustBe <XmlTestOutput serverzeit="05.02.2024">
        <requiredTestStringConcat>
          {concatVal1 + concatVal2}</requiredTestStringConcat>
        <requiredIntSquared>
          {squareTest * squareTest}</requiredIntSquared>
      </XmlTestOutput>.normalize
    }

    "route to xml test endpoint with external client and throw error because of missing attribute" in {
      val xml     =
        <pouebergabe serverzeit="05.02.2024">
        </pouebergabe>
      val request = route(
        app,
        FakeRequest("POST", s"/xml/Test2")
          .withHeaders(("content-type", "application/xml"))
          .withXmlBody(
            xml
          )
      ).get
      status(request) mustBe 400
      val result  = scala.xml.XML.loadString(contentAsString(request))
      result.normalize mustBe <ContextRouteError><message>Expected a single node with text content (path: .pouebergabe.requiredTest)</message></ContextRouteError>.normalize
    }

    "route to xml test endpoint with external client and set json header but send xml" in {
      val xml     =
        <pouebergabe serverzeit="05.02.2024">
        </pouebergabe>
      val request = route(
        app,
        FakeRequest("POST", s"/xml/Test2")
          .withHeaders(("content-type", "application/json"))
          .withXmlBody(
            xml
          )
      ).get
      status(request) mustBe 400
      val result  = contentAsJson(request)
      result.toString() mustBe
        "{\"message\":\"Expected JSON object: (path: .)\",\"status\":{\"headers\":{},\"statusCode\":400}," +
        "\"contentType\":\"application/json\"}"
    }

    "route to test endpoint with external client and set xml header but send " in {
      implicit val format = Json.format[Pouebergabe]

      val request = route(
        app,
        FakeRequest("POST", s"/xml/Test2")
          .withHeaders(("content-type", "application/xml"))
          .withJsonBody(
            Json.toJson(Pouebergabe("05.02.2024", "ThisShouldNotWork", Some(10)))
          )
      ).get
      status(request) mustBe 400
      val result  = scala.xml.XML.loadString(contentAsString(request))
      result.normalize mustBe <ContextRouteError><message>
        {"Could not parse XML document: unexpected character '{' (path: .)"}</message></ContextRouteError>.normalize
    }

    "route to test endpoint with external client and json protocol" in {
      implicit val formatI: OFormat[Pouebergabe]   = Json.format[Pouebergabe]
      implicit val formatO: OFormat[XmlTestOutput] = Json.format[XmlTestOutput]
      val concatVal2                               = "Test2"
      val concatVal1                               = "ConcatThis"
      val squareTest                               = Some(15)
      val date = "05.02.2024"
      val request                                  = route(
        app,
        FakeRequest("POST", s"/xml/$concatVal2")
          .withHeaders(("content-type", "application/json"))
          .withJsonBody(
            Json.toJson(Pouebergabe(date, concatVal1, squareTest))
          )
      ).get
      status(request) mustBe 200
      val result                                   = contentAsJson(request).as[XmlTestOutput]
      result.requiredTestStringConcat mustBe concatVal1 + concatVal2
      result.requiredIntSquared mustBe squareTest.map(s => s * s)
      result.serverzeit mustBe date
    }

  }
}
