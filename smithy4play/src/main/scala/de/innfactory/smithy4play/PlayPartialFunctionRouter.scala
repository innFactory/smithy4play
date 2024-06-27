package de.innfactory.smithy4play

import play.api.mvc.RequestHeader
import smithy4s.Endpoint
import smithy4s.capability.MonadThrowLike
import smithy4s.http.{ HttpEndpoint, HttpMethod, HttpUri, PathParams }
import smithy4s.kinds.FunctorInterpreter
import smithy4s.server.UnaryServerCodecs
import smithy.smithy4play.ContentTypes

class PlayPartialFunctionRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
  _
], RequestHead <: RequestHeader, Request, Response](
  service: smithy4s.Service.Aux[Alg, Op],
  impl: FunctorInterpreter[Op, F],
  codecs: ContentType => UnaryServerCodecs.Make[F, Request, Response],
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

  private def makeHttpEndpointHandler[I, E, O, SI, SO](
    endpoint: service.Endpoint[I, E, O, SI, SO]
  ): Either[HttpEndpoint.HttpEndpointError, HttpEndpointHandler] =
    HttpEndpoint.cast(endpoint.schema).map { httpEndpoint =>
      val func = (v: RequestHead) => {
        val serviceHints          = service.hints
        val endpointHints         = endpoint.hints
        val tag                   = ContentTypes.tagInstance
        val jsonContentType       = "application/json"
        val jsonContentTypes      = List("application/json")
        val supportedContentTypes = endpointHints
          .get(tag)
          .orElse(serviceHints.get(tag))
          .getOrElse(
            ContentTypes(jsonContentTypes, jsonContentTypes, jsonContentTypes)
          )

        val preferredInput  = supportedContentTypes.input.headOption.getOrElse(jsonContentType)
        val preferredOutput = supportedContentTypes.output.headOption.getOrElse(jsonContentType)
        val preferredError  = supportedContentTypes.error.headOption.getOrElse(jsonContentType)

        val accept: Seq[String] = v.acceptedTypes.map(range => range.mediaType + "/" + range.mediaSubType)
        val accepted            = accept.find(v => supportedContentTypes.output.map(_.toLowerCase).contains(v.toLowerCase))
        val errorAccepted       = accept.find(v => supportedContentTypes.error.map(_.toLowerCase).contains(v.toLowerCase))
        val inputHeaderContent  = v.contentType
        val inputContentType    = inputHeaderContent
          .flatMap(v => supportedContentTypes.input.map(_.toLowerCase).find(_ == v.toLowerCase))

        val contentType = ContentType(
          inputContentType.getOrElse(preferredInput),
          accepted.getOrElse(preferredOutput),
          errorAccepted.getOrElse(preferredError)
        )

        val codec: UnaryServerCodecs.Make[F, Request, Response] = codecs(contentType)

        smithy4s.server.UnaryServerEndpoint(
          impl,
          endpoint,
          codec(endpoint.schema),
          endpointMiddleware.prepare(service)(endpoint)
        )
      }
      HttpEndpointHandler(
        httpEndpoint,
        func
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
    codecs: ContentType => UnaryServerCodecs.Make[F, Request, Response],
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
