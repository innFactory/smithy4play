package de.innfactory.smithy4play.routing

import de.innfactory.smithy4play.codecs.EndpointContentTypes
import de.innfactory.smithy4play.meta.ContentTypes
import play.api.mvc.RequestHeader
import smithy4s.capability.MonadThrowLike
import smithy4s.http.{HttpEndpoint, HttpMethod, HttpUri, PathParams}
import smithy4s.kinds.FunctorInterpreter
import smithy4s.server.UnaryServerCodecs
import smithy4s.{Endpoint, Hints}

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
    resolveEndpointContentTypes(supportedTypes, requestHeader)
  }

  private def makeHttpEndpointHandler[I, E, O, SI, SO](
    endpoint: service.Endpoint[I, E, O, SI, SO]
  ): Either[HttpEndpoint.HttpEndpointError, HttpEndpointHandler] =
    HttpEndpoint.cast(endpoint.schema).map { httpEndpoint =>
      HttpEndpointHandler(
        httpEndpoint,
        (v: RequestHead) => {
          val contentType = resolveContentType(endpoint.hints, service.hints, v)
          val codec       = codecs(contentType)
          smithy4s.server.UnaryServerEndpoint(
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
