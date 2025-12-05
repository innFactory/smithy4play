package models

import cats.data.EitherT
import de.innfactory.smithy4play.client.{FinishedClientResponse, RunnableClientResponse, SmithyPlayClient, matchStatusCodeForResponse}
import org.apache.pekko.stream.Materializer
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, writeableOf_AnyContentAsEmpty}
import smithy4s.client.UnaryLowLevelClient
import smithy4s.http.{CaseInsensitive, HttpRequest, HttpResponse}
import smithy4s.kinds.Kind1
import smithy4s.{Blob, Endpoint, Hints}

import scala.concurrent.ExecutionContext

class Smithy4PlayTestClient[Alg[_[_, _, _, _, _]]](
  val service: smithy4s.Service[Alg],
  middleware: Endpoint.Middleware[Smithy4PlayTestClient[Alg]],
  requestIsSuccessful: (Hints, HttpResponse[Blob]) => Boolean = matchStatusCodeForResponse,
  explicitDefaultsEncoding: Boolean = true
)(implicit ec: ExecutionContext, app: Application, mat: Materializer)
    extends UnaryLowLevelClient[FinishedClientResponse, HttpRequest[Blob], HttpResponse[Blob]] {

  val underlyingClient = new SmithyPlayClient[Alg, Smithy4PlayTestClient[Alg]](
    baseUri = "",
    service = service,
    client = this,
    middleware = middleware,
    requestIsSuccessful = requestIsSuccessful,
    toSmithy4sClient = x => x
  )

  def transformer(): Alg[Kind1[RunnableClientResponse]#toKind5] =
    underlyingClient.service.algebra(underlyingClient.compiler)

  private def buildPath(req: HttpRequest[Blob]): String =
    req.uri.path.mkString("/", "/", "") + toQuery(req)

  private def toQuery(req: HttpRequest[Blob]) = {
    val queryList = toQueryParameters(req).map(s => s._1 + "=" + s._2)
    if (queryList.nonEmpty) queryList.mkString("?", "&", "") else ""
  }

  private def toHeaders(request: HttpRequest[Blob]): List[(String, String)] =
    request.headers.toList.flatMap { case (insensitive, strings) =>
      strings.map(v => (insensitive.value, v))
    }

  private def toQueryParameters(request: HttpRequest[Blob]): List[(String, String)] =
    request.uri.queryParams.toList.flatMap { case (key, value) =>
      value.map(v => (key, v))
    }

  override def run[Output](
    request: HttpRequest[Blob]
  )(responseCB: HttpResponse[Blob] => FinishedClientResponse[Output]): FinishedClientResponse[Output] = {
    val baseRequest: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest(method = request.method.showUppercase, buildPath(request))
        .withHeaders(toHeaders(request)*)
    val res                                              =
      if (!request.body.isEmpty) route(app, baseRequest.withBody(request.body.toArray)).get
      else
        route(
          app,
          baseRequest
        ).get

    val httpResponse = for {
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

    EitherT {
      httpResponse.flatMap { httpResponse =>
        responseCB(httpResponse).value
      }
    }

  }
}

object Smithy4PlayTestClient {
  def apply[Alg[_[_, _, _, _, _]]](
    service: smithy4s.Service[Alg],
    middleware: Endpoint.Middleware[Smithy4PlayTestClient[Alg]],
    requestIsSuccessful: (Hints, HttpResponse[Blob]) => Boolean = matchStatusCodeForResponse,
    explicitDefaultsEncoding: Boolean = true
  )(implicit ec: ExecutionContext, app: Application, mat: Materializer): Alg[Kind1[RunnableClientResponse]#toKind5] =
    new Smithy4PlayTestClient(service, middleware, requestIsSuccessful, explicitDefaultsEncoding).transformer()
}
