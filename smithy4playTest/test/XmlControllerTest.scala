
import models.NodeImplicits.NodeEnhancer
import models.TestBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Json, OFormat }
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import smithy4s.http.CaseInsensitive
import testDefinitions.test.{XmlControllerDefGen, XmlTestInputBody, XmlTestOutput}
import de.innfactory.smithy4play.client.SmithyPlayTestUtils.*

import scala.concurrent.ExecutionContext.Implicits.global

class XmlControllerTest extends TestBase {

  val genericClient = client(XmlControllerDefGen.service)

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "controller.XmlController" must {

    "route to xml test endpoint" in {
      val res = genericClient
        .xmlTestWithInputAndOutput(
          "Concat",
          XmlTestInputBody("05.02.2024", "ThisGets", Some(10))
        )
        .awaitRight
      res.body.body.requiredIntSquared mustBe Some(100)
      res.headers.get(CaseInsensitive("content-type")) mustBe Some(List("application/xml"))
      res.body.body.requiredTestStringConcat mustBe "ThisGetsConcat"
    }

    "route to xml with charset in header endpoint with smithy client" in {
      val res = genericClient
        .xmlTestWithInputAndOutput(
          "Concat",
          XmlTestInputBody("05.02.2024", "ThisGets", Some(10)),
          Some("application/xml; charset=utf-8")
        )
        .awaitRight

      res.body.body.requiredIntSquared mustBe Some(100)
      res.body.body.requiredTestStringConcat mustBe "ThisGetsConcat"
      res.headers.get(CaseInsensitive("content-type")) mustBe Some(List("application/xml; charset=utf-8"))
    }

    "route to xml with charset in header with external client" in {
      val concatVal1 = "ConcatThis"
      val concatVal2 = "Test2"
      val squareTest = 3
      val xml        =
        <XmlTestInputBody serverzeit="05.02.2024">
          <requiredTest>{concatVal1}</requiredTest>
          <requiredInt>{squareTest}</requiredInt>
        </XmlTestInputBody>
      val request    = route(
        app,
        FakeRequest("POST", s"/xml/$concatVal2")
          .withHeaders(("content-type", "application/xml; charset=utf-8"))
          .withXmlBody(
            xml
          )
      ).get
      status(request) mustBe 200

      val result  = scala.xml.XML.loadString(contentAsString(request))
      val resContentType = contentType(request)
      val resCharset = charset(request)


      result.normalize mustBe <XmlTestOutput serverzeit="05.02.2024">
        <requiredTestStringConcat>
          {concatVal1 + concatVal2}</requiredTestStringConcat>
        <requiredIntSquared>
          {squareTest * squareTest}</requiredIntSquared>
      </XmlTestOutput>.normalize
      resContentType.map(_ + "; charset=" + resCharset.getOrElse("")) mustBe Some("application/xml; charset=utf-8")
    }

    "route to xml test endpoint with external client" in {
      val concatVal1 = "ConcatThis"
      val concatVal2 = "Test2"
      val squareTest = 3
      val xml        =
        <XmlTestInputBody serverzeit="05.02.2024">
          <requiredTest>{concatVal1}</requiredTest>
          <requiredInt>{squareTest}</requiredInt>
        </XmlTestInputBody>
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
        <XmlTestInputBody serverzeit="05.02.2024">
        </XmlTestInputBody>
      val request = route(
        app,
        FakeRequest("POST", s"/xml/Test2")
          .withHeaders(("content-type", "application/xml"))
          .withXmlBody(
            xml
          )
      ).get
      status(request) mustBe 400
      println(contentAsString(request))
//      val result  = scala.xml.XML.loadString(contentAsString(request))
//      result.normalize mustBe <ContextRouteError><message>Expected a single node with text content (path: .XmlTestInputBody.requiredTest)</message></ContextRouteError>.normalize
    }

    "route to xml test endpoint with external client and set json header but send xml" in {
      val xml     =
        <XmlTestInputBody serverzeit="05.02.2024">
        </XmlTestInputBody>
      val request = route(
        app,
        FakeRequest("POST", s"/xml/Test2")
          .withHeaders(("content-type", "application/json"))
          .withHeaders(("accept", "application/json"))
          .withXmlBody(
            xml
          )
      ).get
      status(request) mustBe 400
      val result  = contentAsJson(request)
    }

    "route to test endpoint with external client and set xml header but send " in {
      implicit val format = Json.format[XmlTestInputBody]

      val request = route(
        app,
        FakeRequest("POST", s"/xml/Test2")
          .withHeaders(("content-type", "application/xml"))
          .withJsonBody(
            Json.toJson(XmlTestInputBody("05.02.2024", "ThisShouldNotWork", Some(10)))
          )
      ).get
      status(request) mustBe 400
    }

    "route to test endpoint with external client and json protocol" in {
      implicit val formatI: OFormat[XmlTestInputBody] = Json.format[XmlTestInputBody]
      implicit val formatO: OFormat[XmlTestOutput]    = Json.format[XmlTestOutput]
      val concatVal2                                  = "Test2"
      val concatVal1                                  = "ConcatThis"
      val squareTest                                  = Some(15)
      val date                                        = "05.02.2024"
      val request                                     = route(
        app,
        FakeRequest("POST", s"/xml/$concatVal2")
          .withHeaders(("content-type", "application/json"))
          .withHeaders(("accept", "application/json"))
          .withJsonBody(
            Json.toJson(XmlTestInputBody(date, concatVal1, squareTest))
          )
      ).get
      status(request) mustBe 200
      val result                                      = contentAsJson(request).as[XmlTestOutput]
      result.requiredTestStringConcat mustBe concatVal1 + concatVal2
      result.requiredIntSquared mustBe squareTest.map(s => s * s)
      result.serverzeit mustBe date
    }

  }
}
