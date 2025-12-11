package de.innfactory.smithy4play.routing.internal

import de.innfactory.smithy4play.codecs.EndpointContentTypes
import de.innfactory.smithy4play.telemetry.Telemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import play.api.mvc.RequestHeader
import smithy4s.capability.MonadThrowLike
import smithy4s.http.{ HttpEndpoint, HttpMethod, HttpUri, PathParams }
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

// Port of smithy4s.http.PartialFunctionRouter to allow more flexibility (specific codecs, middleware)
// when using smithy4s/smithy4play with play framework
class PlayPartialFunctionRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
  _
], RequestHead <: RequestHeader, Request, Response](
  service: smithy4s.Service.Aux[Alg, Op],
  impl: FunctorInterpreter[Op, F],
  codecs: EndpointContentTypes => UnaryServerCodecs.Make[F, Request, Response],
  endpointMiddleware: Endpoint.Middleware[Request => F[Response]],
  getMethod: RequestHead => HttpMethod,
  getUri: RequestHead => HttpUri,
  addDecodedPathParams: (Request, PathParams) => Request
)(implicit F: MonadThrowLike[F])
    extends PartialFunction[RequestHead, Request => F[Response]] {

  private final case class HttpEndpointHandler(
    httpEndpoint: HttpEndpoint[?],
    handler: RequestHead => Request => F[Response]
  )

  def isDefinedAt(requestHead: RequestHead): Boolean = {
    val method       = getMethod(requestHead)
    val pathSegments = getUri(requestHead).path
    perMethodEndpoint.get(method).exists { httpUnaryEndpoints =>
      httpUnaryEndpoints.iterator.exists(_.httpEndpoint.matches(pathSegments).isDefined)
    }
  }

  def apply(requestHead: RequestHead): Request => F[Response] = {
    val method             = getMethod(requestHead)
    val pathSegments       = getUri(requestHead).path
    val httpUnaryEndpoints = perMethodEndpoint(method)
    httpUnaryEndpoints.iterator
      .map(ep => (ep.handler, ep.httpEndpoint.matches(pathSegments)))
      .collectFirst { case (handler, Some(pathParams)) =>
        (request: Request) => handler(requestHead)(addDecodedPathParams(request, pathParams))
      }
      .get
  }

  private def resolveContentType(endpointHints: Hints, serviceHints: Hints, requestHeader: RequestHead) = {
    import de.innfactory.smithy4play.codecs.CodecSupport.*
    val supportedTypes = resolveSupportedTypes(endpointHints, serviceHints)
    resolveEndpointContentTypes(
      supportedTypes,
      requestHeader.acceptedTypes.map(v => v.mediaType + "/" + v.mediaSubType),
      requestHeader.contentType
    )
  }

  private def makeHttpEndpointHandler[I, E, O, SI, SO](
    endpoint: service.Endpoint[I, E, O, SI, SO]
  ): Either[HttpEndpoint.HttpEndpointError, HttpEndpointHandler] =
    HttpEndpoint.cast(endpoint.schema).map { httpEndpoint =>
      HttpEndpointHandler(
        httpEndpoint,
        (v: RequestHead) => {
          val span        = Span.current()
          val pathName    = httpEndpoint.path.map(_.toString).mkString("/")
          if (pathName.isBlank) {
            span.updateName("/")
          } else {
            span.updateName(pathName)
          }
          span.setAttribute("http/method", httpEndpoint.method.showUppercase)
          val contentType = resolveContentType(endpoint.hints, service.hints, v)
          val codec       = codecs(contentType)
          Smithy4PlayServerEndpoint(
            impl,
            endpoint,
            codec(endpoint.schema),
            endpointMiddleware.prepare(service)(endpoint)
          )
        }
      )
    }

  private val httpEndpointHandlers: List[HttpEndpointHandler] =
    service.endpoints.toList.map {
      makeHttpEndpointHandler(_)
    }.collect { case Right(endpointWrapper) => endpointWrapper }

  private val perMethodEndpoint: Map[HttpMethod, List[HttpEndpointHandler]] =
    httpEndpointHandlers.groupBy(_.httpEndpoint.method)

}

object PlayPartialFunctionRouter {
  def partialFunction[Alg[_[_, _, _, _, _]], F[_], RequestHead <: RequestHeader, Request, Response](
    service: smithy4s.Service[Alg]
  )(
    impl: service.Impl[F],
    codecs: EndpointContentTypes => UnaryServerCodecs.Make[F, Request, Response],
    endpointMiddleware: Endpoint.Middleware[Request => F[Response]],
    getMethod: RequestHead => HttpMethod,
    getUri: RequestHead => HttpUri,
    addDecodedPathParams: (Request, PathParams) => Request
  )(implicit F: MonadThrowLike[F]): PartialFunction[RequestHead, Request => F[Response]] =
    new PlayPartialFunctionRouter[Alg, service.Operation, F, RequestHead, Request, Response](
      service,
      service.toPolyFunction[smithy4s.kinds.Kind1[F]#toKind5](impl),
      codecs,
      endpointMiddleware,
      getMethod,
      getUri,
      addDecodedPathParams
    )
}
