package de.innfactory.smithy4play.routing

import cats.data.{EitherT, Kleisli}
import de.innfactory.smithy4play.{ContextRoute, RoutingResult}
import play.api.mvc.{Headers, RawBuffer, Request, RequestHeader, Result}
import smithy4s.Blob
import smithy4s.http.{CaseInsensitive, HttpEndpoint, HttpMethod, HttpRequest, HttpUri, HttpUriScheme}

import scala.concurrent.ExecutionContext

package object internal {
  private[routing] def getHeaders(headers: Headers): Map[CaseInsensitive, Seq[String]] =
    headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  private[routing] def matchRequestPath(
    requestHeader: RequestHeader,
    ep: HttpEndpoint[?]
  ): Option[Map[String, String]] =
    ep.matches(deconstructPath(requestHeader.path))

  private[routing] def deconstructPath(path: String) =
    path.replaceFirst("/", "").split("/").filter(_.nonEmpty)

  private[routing] def getQueryParams(requestHeader: RequestHeader): Map[String, Seq[String]] =
    requestHeader.queryString

  private[routing] def getSmithy4sHttpMethod(method: String): HttpMethod =
    HttpMethod.fromStringOrDefault(method.toUpperCase)

  private[smithy4play] def toSmithy4sHttpRequest(
    request: Request[RawBuffer]
  )(implicit ec: ExecutionContext): ContextRoute[HttpRequest[Blob]] =
    Kleisli { ctx =>
      val pathParams = deconstructPath(request.path)
      val uri        = toSmithy4sHttpUri(pathParams, request.secure, request.host, request.queryString)
      val headers    = getHeaders(request.headers)
      val method     = getSmithy4sHttpMethod(request.method)
      val parsedBody = request.body.asBytes().map(b => Blob(b.toByteBuffer)).getOrElse(Blob.empty)
      EitherT.rightT(HttpRequest(method, uri, headers, parsedBody))
    }

  private[smithy4play] def toSmithy4sHttpUri(
    path: IndexedSeq[String],
    secure: Boolean,
    host: String,
    queryString: Map[String, Seq[String]]
  ): HttpUri = {
    val uriScheme = if (secure) HttpUriScheme.Https else HttpUriScheme.Http
    HttpUri(
      uriScheme,
      host,
      None,
      path,
      queryString,
      None
    )
  }

  type InternalRoute = PartialFunction[RequestHeader, Request[RawBuffer] => RoutingResult[Result]]

}
