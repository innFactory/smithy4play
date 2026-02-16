package de.innfactory.smithy4play.routing

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.{ ContextRoute, RoutingResult }
import play.api.mvc.{ Headers, RawBuffer, Request, RequestHeader, Result }
import smithy4s.Blob
import smithy4s.http.{ CaseInsensitive, HttpEndpoint, HttpMethod, HttpRequest, HttpUri, HttpUriScheme, PathParams }

import scala.concurrent.ExecutionContext

package object internal {

  case class RequestWrapped(
    req: Request[RawBuffer],
    pathParams: PathParams,
    parsedPathSegments: IndexedSeq[String] = null
  ) {

    /** Holder for lazy body parsing. The actual Blob is only created when `lazyBodyHolder.blob` is accessed.
      */
    private lazy val lazyBodyHolder: LazyBlobHolder = LazyBlobHolder(req.body)

    /** Lazily parsed body - only materialized when accessed. This avoids unnecessary memory copies for requests where
      * the body is not needed.
      */
    def lazyBody: Blob = lazyBodyHolder.blob

    /** Check body size without materializing it.
      */
    def bodySize: Long = lazyBodyHolder.size

    /** Check if body is empty without materializing it.
      */
    def isBodyEmpty: Boolean = lazyBodyHolder.isEmpty

    /** Get path segments, using pre-parsed segments if available to avoid redundant deconstructPath calls.
      */
    def pathSegments: IndexedSeq[String] =
      if (parsedPathSegments != null) parsedPathSegments
      else deconstructPath(req.path)
  }

  /** Convert Play headers to Smithy4s format. Uses groupMap for single-pass conversion (avoids intermediate map from
    * groupBy).
    */
  private[routing] def getHeaders(headers: Headers): Map[CaseInsensitive, Seq[String]] =
    headers.headers.groupMap(h => CaseInsensitive(h._1))(_._2)

  private[routing] def matchRequestPath(
    requestHeader: RequestHeader,
    ep: HttpEndpoint[?]
  ): Option[Map[String, String]] =
    ep.matches(deconstructPath(requestHeader.path))

  /** Parse a URL path into segments.
    *
    * Performance note: This allocates a new array. The routing layer caches the result in CachedMatch to avoid
    * redundant parsing.
    */
  private[routing] def deconstructPath(path: String): IndexedSeq[String] =
    if (path == null || path.isEmpty || path == "/") {
      IndexedSeq.empty
    } else
      {
        if (path.charAt(0) == '/') {
          path.drop(1)
        } else {
          path
        }
      }.split("/").filter(_.nonEmpty)

  private[routing] def getQueryParams(requestHeader: RequestHeader): Map[String, Seq[String]] =
    requestHeader.queryString

  private[routing] def getSmithy4sHttpMethod(method: String): HttpMethod =
    HttpMethod.fromStringOrDefault(method.toUpperCase)

  /** Convert a wrapped request to Smithy4s HttpRequest. Uses lazy body parsing to avoid unnecessary memory copies. Uses
    * pre-parsed path segments from routing when available to avoid redundant deconstructPath calls.
    */
  private[smithy4play] def toSmithy4sHttpRequest(
    request: RequestWrapped
  )(implicit ec: ExecutionContext): ContextRoute[HttpRequest[Blob]] =
    Kleisli { ctx =>
      val segments = request.pathSegments
      val uri      =
        toSmithy4sHttpUri(segments, request.req.secure, request.req.host, request.req.queryString, request.pathParams)
      val headers  = getHeaders(request.req.headers)
      val method   = getSmithy4sHttpMethod(request.req.method)

      // Use lazy body - only materialized when codec actually reads it
      val body = request.lazyBody

      EitherT.rightT(HttpRequest(method, uri, headers, body))
    }

  private[smithy4play] def toSmithy4sHttpUri(
    path: IndexedSeq[String],
    secure: Boolean,
    host: String,
    queryString: Map[String, Seq[String]],
    pathParams: PathParams
  ): HttpUri = {
    val uriScheme = if (secure) HttpUriScheme.Https else HttpUriScheme.Http
    HttpUri(
      uriScheme,
      host,
      None,
      path,
      queryString,
      if (pathParams.nonEmpty) Some(pathParams) else None
    )
  }

  type InternalRoute = PartialFunction[RequestHeader, Request[RawBuffer] => RoutingResult[Result]]

  case class PathNotFound() extends Throwable

  def acceptedContentTypesForRequestHeader(requestHeader: RequestHeader): Seq[String] =
    requestHeader.acceptedTypes.map(range => range.mediaType + "/" + range.mediaSubType)

  def contentTypeForRequestHeader(requestHeader: RequestHeader): Option[String] =
    requestHeader.contentType

}
