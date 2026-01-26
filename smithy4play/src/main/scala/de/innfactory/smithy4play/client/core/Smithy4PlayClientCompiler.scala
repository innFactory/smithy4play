package de.innfactory.smithy4play.client.core

import de.innfactory.smithy4play.client.{ ClientResponse, FinishedClientResponse, RunnableClientResponse }
import de.innfactory.smithy4play.codecs.EndpointContentTypes
import smithy4s.{ Blob, Endpoint, Hints }
import smithy4s.capability.MonadThrowLike
import smithy4s.client.UnaryLowLevelClient
import smithy4s.http.{ HttpRequest, HttpResponse, HttpUnaryClientCodecs }

import scala.concurrent.ExecutionContext

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
    toSmithy4sClient: Client => UnaryLowLevelClient[FinishedClientResponse, HttpRequest[Blob], HttpResponse[Blob]],
    codecs: EndpointContentTypes => HttpUnaryClientCodecs.Builder[ClientResponse, HttpRequest[Blob], HttpResponse[
      Blob
    ]],
    middleware: Endpoint.Middleware[Client],
    isSuccessful: (Hints, HttpResponse[Blob]) => Boolean
  )(implicit
    F: MonadThrowLike[ClientResponse],
    ec: ExecutionContext
  ): service.FunctorEndpointCompiler[RunnableClientResponse] =
    new service.FunctorEndpointCompiler[RunnableClientResponse] {
      def apply[I, E, O, SI, SO](
        endpoint: service.Endpoint[I, E, O, SI, SO]
      ): I => RunnableClientResponse[O] = {

        val transformedClient =
          middleware.prepare(service)(endpoint).apply(client)

        val adaptedClient = toSmithy4sClient(transformedClient)
        val contentType   = resolveContentType(endpoint.hints, service.hints, Seq.empty, None)
        val codec         = codecs(contentType).build()

        val clientEndpoint: I => RunnableClientResponse[O] = Smithy4PlayClientEndpoint(
          adaptedClient,
          codec.apply(endpoint.schema),
          isSuccessful.curried(endpoint.hints)
        )

        clientEndpoint
      }
    }

}
