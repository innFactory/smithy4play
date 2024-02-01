import de.innfactory.smithy4play.client.GenericAPIClient.EnhancedGenericAPIClient
import de.innfactory.smithy4play.client.RequestClient
import de.innfactory.smithy4play.client.SmithyPlayTestUtils._
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.Application
import play.api.Play.materializer
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsXml}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import smithy4s.Blob
import smithy4s.http.{CaseInsensitive, HttpResponse}
import testDefinitions.test.{XmlControllerDefGen, XmlTestInputBody}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.xml.{Node, NodeSeq}

class XmlControllerTest extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory {

  implicit object FakeRequestClient extends RequestClient {
    override def send(
      method: String,
      path: String,
      headers: Map[String, Seq[String]],
      body: Blob
    ): Future[HttpResponse[Blob]] = {
      val baseRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(method, path)
        .withHeaders(headers.toList.flatMap(headers => headers._2.map(v => (headers._1, v))): _*)
      val res                                              =
        if (!body.isEmpty) route(app, baseRequest.withBody(body.toArray)).get
        else
          route(
            app,
            baseRequest
          ).get

      for {
        result      <- res
        headers      = result.header.headers.map(v => (CaseInsensitive(v._1), Seq(v._2)))
        body        <- result.body.consumeData.map(_.toArrayUnsafe())
        bodyConsumed = if (result.body.isKnownEmpty) None else Some(body)
        contentType  = result.body.contentType
      } yield HttpResponse(
        result.header.status,
        headers,
        bodyConsumed.map(Blob(_)).getOrElse(Blob.empty)
      ).withContentType(contentType.getOrElse("application/xml"))
    }
  }

  val genericClient = XmlControllerDefGen.withClientAndHeaders(FakeRequestClient, None, List(269))

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "controller.XmlController" must {

    "route to xml test endpoint" in {
      val res = genericClient.xmlTestWithInputAndOutput("Concat", XmlTestInputBody("ThisGets", Some(10))).awaitRight
      res.body.body.requiredIntSquared mustBe Some(100)
      res.headers.get(CaseInsensitive("content-type")) mustBe Some(List("application/xml"))
      res.body.body.requiredTestStringConcat mustBe "ThisGetsConcat"
    }

    "route to xml test endpoint with external client" in {
      val request = route(
        app,
        FakeRequest("POST", "/xml/Test2").withXmlBody(
      <body>
      <requiredTest>ConcatThis</requiredTest>
        <requiredInt>3</requiredInt>
      </body>)
      ).get
      //val x       = request.map(_.body.as("application/xml"))
      val res = Await.result(request, 5.hours)
      println(res)
      status(request) mustBe 200
    }

  }
}
