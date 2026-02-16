package de.innfactory.smithy4play.routing.internal

import de.innfactory.smithy4play.codecs.{ CodecSupport, EndpointContentTypes }
import io.opentelemetry.api.trace.Span
import play.api.Logging
import play.api.mvc.RequestHeader
import smithy4s.capability.MonadThrowLike
import smithy4s.http.{ HttpEndpoint, HttpMethod, PathParams }
import smithy4s.kinds.FunctorInterpreter
import smithy4s.server.UnaryServerCodecs
import smithy4s.{ Endpoint, Hints }

/*
 *  Copyright 2021-2024 Disney Streaming
 *
 *  Licensed under the Tomorrow Open Source Technology License, Version 1.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     https://disneystreaming.github.io/TOST-1.0.txt
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/** Cached match result to avoid double route matching between isDefinedAt and apply.
  */
private final case class CachedMatch[RequestHead <: RequestHeader, Request, F[_], Response](
  requestHead: RequestHead,
  handler: RequestHead => Request => F[Response],
  pathParams: PathParams,
  pathName: String,
  pathSegments: IndexedSeq[String]
)

// Port of smithy4s.http.PartialFunctionRouter to allow more flexibility (specific codecs, middleware)
// when using smithy4s/smithy4play with play framework
//
// Performance optimizations:
// - Caches match results between isDefinedAt and apply calls
// - Pre-parses path segments once per request
// - Groups endpoints by HTTP method for O(methods) + O(endpoints/method) lookup
class PlayPartialFunctionRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
  _
], RequestHead <: RequestHeader, Request, Response](
  service: smithy4s.Service.Aux[Alg, Op],
  impl: FunctorInterpreter[Op, F],
  codecs: EndpointContentTypes => UnaryServerCodecs.Make[F, Request, Response],
  endpointMiddleware: Endpoint.Middleware[Request => F[Response]],
  addDecodedPathParams: (Request, PathParams, IndexedSeq[String]) => Request
)(implicit F: MonadThrowLike[F])
    extends PartialFunction[RequestHead, Request => F[Response]]
    with Logging {

  private final case class HttpEndpointHandler(
    httpEndpoint: HttpEndpoint[?],
    handler: RequestHead => Request => F[Response],
    pathName: String
  )

  /** Thread-local cache for match results. This eliminates the double route matching that occurs when Play calls
    * isDefinedAt followed by apply.
    */
  private val matchCache = new ThreadLocal[CachedMatch[RequestHead, Request, F, Response]]()

  /** Find a matching endpoint for the request, using cache if available. Returns the handler, path parameters,
    * and pre-parsed path segments if found.
    */
  private def findMatch(requestHead: RequestHead): Option[(HttpEndpointHandler, PathParams, IndexedSeq[String])] = {
    // Check cache first
    val cached = matchCache.get()
    if (cached != null && (cached.requestHead eq requestHead)) {
      return Some((HttpEndpointHandler(null, cached.handler, cached.pathName), cached.pathParams, cached.pathSegments))
    }

    // Cache miss - perform path parsing once directly instead of via lambdas
    val method       = getSmithy4sHttpMethod(requestHead.method)
    val pathSegments = deconstructPath(requestHead.path)

    if (logger.isDebugEnabled) {
      logger.debug("Cache miss for request head, performing match")
      logger.debug("Finding match for method: " + method + " and path segments: " + pathSegments)
      logger.debug("per method endpoint map: " + perMethodEndpoint)
    }

    val result = perMethodEndpoint.get(method).flatMap { httpUnaryEndpoints =>
      httpUnaryEndpoints.iterator.flatMap { ep =>
        ep.httpEndpoint.matches(pathSegments).map(params => (ep, params))
      }
        .nextOption()
    }

    if (logger.isDebugEnabled) {
      logger.debug("saving match result to cache: " + result)
    }

    // Cache the result (including pre-parsed path segments) for the subsequent apply() call
    result.foreach { case (handler, pathParams) =>
      matchCache.set(CachedMatch(requestHead, handler.handler, pathParams, handler.pathName, pathSegments))
    }

    result.map { case (handler, pathParams) => (handler, pathParams, pathSegments) }
  }

  /** Clear the match cache. Should be called after apply() completes.
    */
  private def clearCache(): Unit =
    matchCache.remove()

  def isDefinedAt(requestHead: RequestHead): Boolean =
    findMatch(requestHead).isDefined

  def apply(requestHead: RequestHead): Request => F[Response] =
    try {
      val (handler, pathParams, pathSegments) = findMatch(requestHead).get
      (request: Request) => handler.handler(requestHead)(addDecodedPathParams(request, pathParams, pathSegments))
    } finally clearCache()

  private def resolveContentType(endpointHints: Hints, serviceHints: Hints, requestHeader: RequestHead) = {
    import de.innfactory.smithy4play.codecs.CodecSupport.*
    val supportedTypes = resolveSupportedTypes(endpointHints, serviceHints)
    resolveEndpointContentTypes(
      supportedTypes,
      requestHeader.acceptedTypes.view.map(v => s"${v.mediaType}/${v.mediaSubType}").toSeq,
      requestHeader.contentType
    )
  }

  private def makeHttpEndpointHandler[I, E, O, SI, SO](
    endpoint: service.Endpoint[I, E, O, SI, SO]
  ): Either[HttpEndpoint.HttpEndpointError, HttpEndpointHandler] =
    HttpEndpoint.cast(endpoint.schema).map { httpEndpoint =>
      // Pre-compute path name at registration time (not per-request)
      val pathName = {
        val computed = httpEndpoint.path.map(_.toString).mkString("/")
        if (computed.isBlank) "/" else computed
      }

      // Pre-compute codec for JSON-only endpoints (the common case).
      // This avoids per-request content-type resolution and codec construction.
      val isJsonOnly       = CodecSupport.isJsonOnlyEndpoint(endpoint.hints, service.hints)
      val preCompiledCodec =
        if (isJsonOnly) {
          val codec = codecs(EndpointContentTypes.JsonOnly)
          Some(codec(endpoint.schema))
        } else None

      // Pre-compute middleware at registration time (same for all requests to this endpoint)
      val endpointMw = endpointMiddleware.prepare(service)(endpoint)

      HttpEndpointHandler(
        httpEndpoint,
        (v: RequestHead) => {
          val span = Span.current()
          span.updateName(pathName)
          span.setAttribute("http/method", httpEndpoint.method.showUppercase)
          preCompiledCodec match {
            case Some(compiledCodec) =>
              // Fast path: JSON-only endpoint with pre-computed codec
              Smithy4PlayServerEndpoint(impl, endpoint, compiledCodec, endpointMw)
            case None               =>
              // Slow path: resolve content type per-request for multi-content-type endpoints
              val contentType = resolveContentType(endpoint.hints, service.hints, v)
              val codec       = codecs(contentType)
              Smithy4PlayServerEndpoint(impl, endpoint, codec(endpoint.schema), endpointMw)
          }
        },
        pathName
      )
    }

  private val httpEndpointHandlers: Array[HttpEndpointHandler] =
    service.endpoints.toList.map {
      makeHttpEndpointHandler(_)
    }.collect { case Right(endpointWrapper) => endpointWrapper }.toArray

  private val perMethodEndpoint: Map[HttpMethod, Array[HttpEndpointHandler]] =
    httpEndpointHandlers.groupBy(_.httpEndpoint.method)

}

object PlayPartialFunctionRouter {
  def partialFunction[Alg[_[_, _, _, _, _]], F[_], RequestHead <: RequestHeader, Request, Response](
    service: smithy4s.Service[Alg]
  )(
    impl: service.Impl[F],
    codecs: EndpointContentTypes => UnaryServerCodecs.Make[F, Request, Response],
    endpointMiddleware: Endpoint.Middleware[Request => F[Response]],
    addDecodedPathParams: (Request, PathParams, IndexedSeq[String]) => Request
  )(implicit F: MonadThrowLike[F]): PartialFunction[RequestHead, Request => F[Response]] =
    new PlayPartialFunctionRouter[Alg, service.Operation, F, RequestHead, Request, Response](
      service,
      service.toPolyFunction[smithy4s.kinds.Kind1[F]#toKind5](impl),
      codecs,
      endpointMiddleware,
      addDecodedPathParams
    )
}
