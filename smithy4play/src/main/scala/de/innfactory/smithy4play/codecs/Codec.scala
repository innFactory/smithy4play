package de.innfactory.smithy4play.codecs

import com.github.plokhotnyuk.jsoniter_scala.core.{ ReaderConfig, WriterConfig }
import de.innfactory.smithy4play.client.{ ClientResponse, RunnableClientRequest }
import de.innfactory.smithy4play.routing.internal.RequestWrapped
import de.innfactory.smithy4play.{ ContentType, ContextRoute }
import play.api.http.MimeTypes
import play.api.mvc.{ RawBuffer, Request, Result }
import smithy4s.Blob
import smithy4s.codecs.{ BlobDecoder, BlobEncoder }
import smithy4s.http.{ HttpRequest, HttpResponse, HttpUnaryClientCodecs, HttpUnaryServerCodecs }
import smithy4s.json.Json
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.server.UnaryServerCodecs
import smithy4s.xml.Xml

import java.util.concurrent.ConcurrentHashMap

trait Codec {

  final case class Encoders(
    errorEncoder: CachedSchemaCompiler[BlobEncoder],
    payloadEncoder: CachedSchemaCompiler[BlobEncoder]
  )

  def buildServerCodecFromBase(codecBuilder: HttpUnaryServerCodecs.Builder[ContextRoute, RequestWrapped, Result])(
    contentType: EndpointContentTypes
  ): UnaryServerCodecs.Make[ContextRoute, RequestWrapped, Result] = buildServerCodec(contentType, codecBuilder)

  def buildClientCodecFromBase(
    codecBuilder: HttpUnaryClientCodecs.Builder[ClientResponse, HttpRequest[Blob], HttpResponse[Blob]]
  )(
    contentTypes: EndpointContentTypes
  ): HttpUnaryClientCodecs.Builder[ClientResponse, HttpRequest[Blob], HttpResponse[Blob]] =
    buildClientCodec(contentTypes, codecBuilder)

  private val hintMask = alloy.SimpleRestJson.protocol.hintMask

  lazy val jsoniterReaderConfig: ReaderConfig = ReaderConfig
  lazy val jsoniterWriterConfig: WriterConfig = WriterConfig

  private lazy val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(Json.jsoniter.withHintMask(hintMask))
    .withJsoniterReaderConfig(jsoniterReaderConfig)
    .withJsoniterWriterConfig(jsoniterWriterConfig)

  val customDecoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobDecoder]] = PartialFunction.empty
  val customEncoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobEncoder]] = PartialFunction.empty

  lazy val stringAndBlobEncoder: CachedSchemaCompiler[BlobEncoder] =
    CachedSchemaCompiler.getOrElse(smithy4s.codecs.StringAndBlobCodecs.encoders, jsonCodecs.encoders)

  lazy val stringAndBlobDecoder: CachedSchemaCompiler[BlobDecoder] =
    CachedSchemaCompiler.getOrElse(smithy4s.codecs.StringAndBlobCodecs.decoders, jsonCodecs.decoders)

  // Pre-computed encoders/decoders for common content types
  private lazy val jsonEncoder: CachedSchemaCompiler[BlobEncoder] = jsonCodecs.encoders
  private lazy val jsonDecoder: CachedSchemaCompiler[BlobDecoder] = jsonCodecs.decoders
  private lazy val xmlEncoder: CachedSchemaCompiler[BlobEncoder]  = Xml.encoders
  private lazy val xmlDecoder: CachedSchemaCompiler[BlobDecoder]  = Xml.decoders

  private val predefinedDecoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobDecoder]] = {
    case ContentType(MimeTypes.JSON) => jsonDecoder
    case ContentType(MimeTypes.XML)  => xmlDecoder
  }

  private val fallbackDecoder: PartialFunction[ContentType, CachedSchemaCompiler[BlobDecoder]] = {
    case ContentType(_) => stringAndBlobDecoder
  }

  private val predefinedEncoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobEncoder]] = {
    case ContentType(MimeTypes.JSON) => jsonEncoder
    case ContentType(MimeTypes.XML)  => xmlEncoder
  }

  private val fallbackEncoder: PartialFunction[ContentType, CachedSchemaCompiler[BlobEncoder]] = {
    case ContentType(_) => stringAndBlobEncoder
  }

  // Memoization cache for encoder lookups
  private val encoderCache = new ConcurrentHashMap[String, CachedSchemaCompiler[BlobEncoder]]()
  private val decoderCache = new ConcurrentHashMap[String, CachedSchemaCompiler[BlobDecoder]]()

  private def encoder(contentType: ContentType): CachedSchemaCompiler[BlobEncoder] = {
    // Fast path for JSON (most common)
    if (contentType.value == MimeTypes.JSON) {
      return jsonEncoder
    }

    // Check cache
    val cached = encoderCache.get(contentType.value)
    if (cached != null) {
      return cached
    }

    // Compute and cache
    val encoder = customEncoders
      .orElse(predefinedEncoders)
      .orElse(fallbackEncoder)(contentType)
    encoderCache.putIfAbsent(contentType.value, encoder)
    encoder
  }

  private def decoder(contentType: ContentType): CachedSchemaCompiler[BlobDecoder] = {
    // Fast path for JSON (most common)
    if (contentType.value == MimeTypes.JSON) {
      return jsonDecoder
    }

    // Check cache
    val cached = decoderCache.get(contentType.value)
    if (cached != null) {
      return cached
    }

    // Compute and cache
    val decoder = customDecoders
      .orElse(predefinedDecoders)
      .orElse(fallbackDecoder)(contentType)
    decoderCache.putIfAbsent(contentType.value, decoder)
    decoder
  }

  private def buildServerCodec(
    contentType: EndpointContentTypes,
    codecBuilder: HttpUnaryServerCodecs.Builder[ContextRoute, RequestWrapped, Result]
  ): UnaryServerCodecs.Make[ContextRoute, RequestWrapped, Result] =
    codecBuilder
      .withResponseMediaType(contentType.output.value)
      .withSuccessBodyEncoders(encoder(contentType.output))
      .withBodyDecoders(decoder(contentType.input))
      .withErrorBodyEncoders(encoder(contentType.error))
      .build()

  private def buildClientCodec[Request, Response](
    contentType: EndpointContentTypes,
    codecBuilder: HttpUnaryClientCodecs.Builder[ClientResponse, Request, Response]
  ) =
    codecBuilder
      .withRequestMediaType(contentType.input.value)
      .withSuccessBodyDecoders(decoder(contentType.output))
      .withBodyEncoders(encoder(contentType.input))
      .withErrorBodyDecoders(decoder(contentType.error))

}
