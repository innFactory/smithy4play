package models

import de.innfactory.smithy4play.EndpointRequest
import de.innfactory.smithy4play.client.RequestClient
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, writeableOf_AnyContentAsEmpty}
import smithy4s.Blob
import smithy4s.http.{CaseInsensitive, HttpResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.materializer


trait TestBase extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory {

  implicit object FakeRequestClient extends RequestClient {
    override def send(
                       method: String,
                       path: String,
                       headers: Map[CaseInsensitive, Seq[String]],
                       result: EndpointRequest
                     ): Future[HttpResponse[Blob]] = {
      val baseRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(method, path)
        .withHeaders(headers.toList.flatMap(headers => headers._2.map(v => (headers._1.toString, v))): _*)
      val res                                              =
        if (!result.body.isEmpty) route(app, baseRequest.withBody(result.body.toArray)).get
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
      ).withContentType(contentType.getOrElse("application/json"))
    }
  }

}
