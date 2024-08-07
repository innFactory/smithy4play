package de.innfactory.smithy4play.client.core

import de.innfactory.smithy4play.client.{OWrapper, RunnableClientRequest}
import de.innfactory.smithy4play.codecs.EndpointContentTypes
import smithy4s.{Blob, Endpoint, Hints}
import smithy4s.capability.MonadThrowLike
import smithy4s.client.{UnaryClientCodecs, UnaryClientEndpoint, UnaryLowLevelClient}
import smithy4s.http.{HttpRequest, HttpResponse, HttpUnaryClientCodecs}
import smithy4s.server.UnaryServerCodecs
import de.innfactory.smithy4play.client.flatMapClientResponse

object Smithy4PlayClientCompiler {

  private def resolveContentType(
    endpointHints: Hints,
    serviceHints: Hints,
    acceptedContentTypes: Seq[String],
    contentTypeHeader: Option[String]
  ) = {
    import de.innfactory.smithy4play.codecs.CodecSupport.*
    val supportedTypes = resolveSupportedTypes(endpointHints, serviceHints)
    resolveEndpointContentTypes(supportedTypes, acceptedContentTypes, contentTypeHeader)
  }

  def apply[Alg[_[_, _, _, _, _]], Client](
    service: smithy4s.Service[Alg],
    client: Client,
    toSmithy4sClient: Client => UnaryLowLevelClient[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]],
    codecs: EndpointContentTypes => HttpUnaryClientCodecs.Builder[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]],
    middleware: Endpoint.Middleware[Client],
    isSuccessful: (Hints, HttpResponse[Blob]) => Boolean
  )(implicit F: MonadThrowLike[RunnableClientRequest]): service.FunctorEndpointCompiler[RunnableClientRequest] = {
 
    new service.FunctorEndpointCompiler[RunnableClientRequest] {
      def apply[I, E, O, SI, SO](
        endpoint: service.Endpoint[I, E, O, SI, SO]
      ): I => RunnableClientRequest[OWrapper[O]] = {

        val transformedClient =
          middleware.prepare(service)(endpoint).apply(client)

        val adaptedClient = toSmithy4sClient(transformedClient)

        val contentType = resolveContentType(endpoint.hints, service.hints, Seq.empty, None)
        val codec = codecs(contentType).build()
       

        val clientEndpoint = UnaryClientEndpoint(
          adaptedClient,
          codec.apply(endpoint.schema),
          isSuccessful.curried(endpoint.hints)
        )
        
        clientEndpoint.andThen(q => {
          q.tapWith((a, b) => OWrapper(b, a()))
        })
      }
    }
  }

}
