package de.innfactory.smithy4play.client

import cats.data.{ EitherT, Kleisli }
import play.api.libs.ws.{ writeableOf_ByteArray, WSClient, WSResponse }
import smithy4s.client.UnaryLowLevelClient
import smithy4s.http.{ CaseInsensitive, HttpRequest, HttpResponse }
import smithy4s.kinds.Kind1
import smithy4s.{ Blob, Endpoint, Hints }

import scala.concurrent.ExecutionContext

class Smithy4PlayWsClient[Alg[_[_, _, _, _, _]]](
  val baseUri: String,
  val service: smithy4s.Service[Alg],
  middleware: Endpoint.Middleware[Smithy4PlayWsClient[Alg]],
  requestIsSuccessful: (Hints, HttpResponse[Blob]) => Boolean = matchStatusCodeForResponse,
  explicitDefaultsEncoding: Boolean = true
)(implicit ec: ExecutionContext, wsClient: WSClient)
    extends UnaryLowLevelClient[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]] {

  val underlyingClient = new SmithyPlayClient[Alg, Smithy4PlayWsClient[Alg]](
    baseUri = baseUri,
    service = service,
    client = this,
    middleware = middleware,
    requestIsSuccessful = (_, _) => true,
    toSmithy4sClient = x => x
  )

  def transformer(): Alg[Kind1[RunnableClientRequest]#toKind5] =
    underlyingClient.service.algebra(underlyingClient.compiler)

  private def buildPath(req: HttpRequest[Blob]): String =
    baseUri + req.uri.path.mkString("/")

  private def toHeaders(request: HttpRequest[Blob]): List[(String, String)] =
    request.headers.flatMap { case (insensitive, strings) =>
      strings.map(v => (insensitive.value, v))
    }.toList

  private def toQueryParameters(request: HttpRequest[Blob]): List[(String, String)] =
    request.uri.queryParams.flatMap { case (key, value) =>
      value.map(v => (key, v))
    }.toList

  private def wsRequestToResponse(res: WSResponse) =
    HttpResponse(
      statusCode = res.status,
      headers = res.headers.map(v => (CaseInsensitive(v._1), v._2.toSeq)),
      body = Blob.apply(res.readableAsByteBuffer.transform(res))
    )

  override def run[Output](
    request: HttpRequest[Blob]
  )(responseCB: HttpResponse[Blob] => RunnableClientRequest[Output]): RunnableClientRequest[Output] = {
    val clientResponse = wsClient
      .url(buildPath(request))
      .withQueryStringParameters(toQueryParameters(request): _*)
      .withMethod(request.method.showUppercase)
      .withHttpHeaders(toHeaders(request): _*)
      .withBody(request.body.toArray)
      .execute()

    Kleisli { headers =>
      EitherT {
        clientResponse.map(wsRequestToResponse).flatMap { httpResponse =>
          responseCB(httpResponse).run(() => httpResponse).value
        }
      }
    }

  }
}

object Smithy4PlayWsClient {
  def apply[Alg[_[_, _, _, _, _]]](
    baseUri: String,
    service: smithy4s.Service[Alg],
    middleware: Endpoint.Middleware[Smithy4PlayWsClient[Alg]],
    requestIsSuccessful: (Hints, HttpResponse[Blob]) => Boolean = matchStatusCodeForResponse,
    explicitDefaultsEncoding: Boolean = true
  )(implicit ec: ExecutionContext, wsClient: WSClient): Alg[Kind1[RunnableClientRequest]#toKind5] =
    new Smithy4PlayWsClient(baseUri, service, middleware, requestIsSuccessful, explicitDefaultsEncoding).transformer()
}
