package de.innfactory.smithy4play.client

import cats.implicits.catsSyntaxApplicativeId
import de.innfactory.smithy4play.client.core.Smithy4PlayClientCompiler
import de.innfactory.smithy4play.codecs.{ Codec, EndpointContentTypes }
import smithy4s.{ Blob, Endpoint, Hints, Service }
import smithy4s.http.{
  HttpDiscriminator,
  HttpMethod,
  HttpRequest,
  HttpResponse,
  HttpUnaryClientCodecs,
  HttpUri,
  HttpUriScheme,
  Metadata
}
import smithy4s.client.UnaryLowLevelClient
import smithy4s.interopcats.monadThrowShim
import smithy4s.schema.FieldFilter.EncodeAll

import scala.concurrent.{ ExecutionContext, Future }

class SmithyPlayClient[Alg[_[_, _, _, _, _]], Client](
  val baseUri: String,
  val service: smithy4s.Service[Alg],
  client: Client,
  middleware: Endpoint.Middleware[Client],
  toSmithy4sClient: Client => UnaryLowLevelClient[FinishedClientResponse, HttpRequest[Blob], HttpResponse[Blob]],
  requestIsSuccessful: (Hints, HttpResponse[Blob]) => Boolean
)(implicit executionContext: ExecutionContext)
    extends Codec {

  private val baseRequest = HttpRequest(
    HttpMethod.POST,
    HttpUri(HttpUriScheme.Http, "localhost", Some(9000), Array.empty[String], Map.empty, None),
    Map.empty,
    Blob.empty
  )

  private val errorHeaders = List(
    smithy4s.http.errorTypeHeader,
    smithy4s.http.amazonErrorTypeHeader
  )

  val clientCodecBuilder: HttpUnaryClientCodecs.Builder[ClientResponse, HttpRequest[Blob], HttpResponse[Blob]] =
    HttpUnaryClientCodecs
      .builder[ClientResponse]
      .withErrorDiscriminator(HttpDiscriminator.fromResponse(errorHeaders, _).pure[ClientResponse])
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(
        Metadata.Encoder.withFieldFilter(EncodeAll)
      )
      .withBaseRequest(_ => baseRequest.pure[ClientResponse])

  val compiledClientCodec
    : EndpointContentTypes => HttpUnaryClientCodecs.Builder[ClientResponse, HttpRequest[Blob], HttpResponse[Blob]] =
    buildClientCodecFromBase(clientCodecBuilder)

  val compiler: service.FunctorEndpointCompiler[RunnableClientResponse] = Smithy4PlayClientCompiler[Alg, Client](
    service = service,
    client = client,
    toSmithy4sClient = toSmithy4sClient,
    codecs = compiledClientCodec,
    middleware = middleware,
    isSuccessful = requestIsSuccessful
  )

}
