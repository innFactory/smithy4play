package de.innfactory.smithy4play.client

import cats.implicits.catsSyntaxApplicativeId
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import de.innfactory.smithy4play.client.RunnableClientRequest
import de.innfactory.smithy4play.client.core.Smithy4PlayClientCompiler
import de.innfactory.smithy4play.codecs.{ Codec, EndpointContentTypes }
import de.innfactory.smithy4play.routing.internal.toSmithy4sHttpUri
import smithy4s.capability.MonadThrowLike
import smithy4s.{ Blob, Endpoint, Hints, Service }
import smithy4s.http.{
  CaseInsensitive,
  HttpDiscriminator,
  HttpEndpoint,
  HttpMethod,
  HttpRequest,
  HttpResponse,
  HttpUnaryClientCodecs,
  HttpUri,
  HttpUriScheme,
  Metadata
}
import smithy4s.client.{ UnaryClientCodecs, UnaryClientCompiler, UnaryClientEndpoint, UnaryLowLevelClient }
import smithy4s.interopcats.monadThrowShim

import scala.concurrent.{ ExecutionContext, Future }

class SmithyPlayClient[Alg[_[_, _, _, _, _]], Client](
  val baseUri: String,
  val service: smithy4s.Service[Alg],
  client: Client,
  middleware: Endpoint.Middleware[Client],
  toSmithy4sClient: Client => UnaryLowLevelClient[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]],
  requestIsSuccessful: (Hints, HttpResponse[Blob]) => Boolean,
  explicitDefaultsEncoding: Boolean = true
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

  val clientCodecBuilder: HttpUnaryClientCodecs.Builder[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]] =
    HttpUnaryClientCodecs
      .builder[RunnableClientRequest]
      .withErrorDiscriminator(HttpDiscriminator.fromResponse(errorHeaders, _).pure[RunnableClientRequest])
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(
        Metadata.Encoder.withExplicitDefaultsEncoding(explicitDefaultsEncoding)
      )
      .withBaseRequest(_ => baseRequest.pure[RunnableClientRequest])

  val compiledClientCodec
    : EndpointContentTypes => HttpUnaryClientCodecs.Builder[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]] =
    buildClientCodecFromBase(clientCodecBuilder)

  val compiler: service.FunctorEndpointCompiler[ClientFinishedResponse] = Smithy4PlayClientCompiler[Alg, Client](
    service = service,
    client = client,
    toSmithy4sClient = toSmithy4sClient,
    codecs = compiledClientCodec,
    middleware = middleware,
    isSuccessful = requestIsSuccessful
  )
  
}
