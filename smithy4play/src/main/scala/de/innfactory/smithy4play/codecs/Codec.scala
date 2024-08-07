package de.innfactory.smithy4play.codecs

import com.github.plokhotnyuk.jsoniter_scala.core.{ReaderConfig, WriterConfig}
import de.innfactory.smithy4play.client.RunnableClientRequest
import de.innfactory.smithy4play.{ContentType, ContextRoute}
import play.api.http.MimeTypes
import play.api.mvc.{RawBuffer, Request, Result}
import smithy4s.Blob
import smithy4s.codecs.{BlobDecoder, BlobEncoder}
import smithy4s.http.{HttpRequest, HttpResponse, HttpUnaryClientCodecs, HttpUnaryServerCodecs}
import smithy4s.json.Json
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.server.UnaryServerCodecs
import smithy4s.xml.Xml

trait Codec {

  final case class Encoders(
    errorEncoder: CachedSchemaCompiler[BlobEncoder],
    payloadEncoder: CachedSchemaCompiler[BlobEncoder]
  )

  def buildServerCodecFromBase(codecBuilder: HttpUnaryServerCodecs.Builder[ContextRoute, Request[RawBuffer], Result])(
    contentType: EndpointContentTypes
  ): UnaryServerCodecs.Make[ContextRoute, Request[RawBuffer], Result] = buildServerCodec(contentType, codecBuilder)

  def buildClientCodecFromBase(
    codecBuilder: HttpUnaryClientCodecs.Builder[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]]
  )(contentTypes: EndpointContentTypes): HttpUnaryClientCodecs.Builder[RunnableClientRequest, HttpRequest[Blob], HttpResponse[Blob]] =
    buildClientCodec(contentTypes, codecBuilder)

  private val hintMask = alloy.SimpleRestJson.protocol.hintMask

  val jsoniterReaderConfig: ReaderConfig.type = ReaderConfig
  val jsoniterWriterConfig: WriterConfig.type = WriterConfig

  private val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(Json.jsoniter.withHintMask(hintMask))
    .withJsoniterReaderConfig(jsoniterReaderConfig)
    .withJsoniterWriterConfig(jsoniterWriterConfig)

  val customDecoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobDecoder]] = PartialFunction.empty
  val customEncoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobEncoder]] = PartialFunction.empty

  val stringAndBlobEncoder: CachedSchemaCompiler[BlobEncoder] =
    CachedSchemaCompiler.getOrElse(smithy4s.codecs.StringAndBlobCodecs.encoders, jsonCodecs.encoders)

  val stringAndBlobDecoder: CachedSchemaCompiler[BlobDecoder] =
    CachedSchemaCompiler.getOrElse(smithy4s.codecs.StringAndBlobCodecs.decoders, jsonCodecs.decoders)

  private val predefinedDecoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobDecoder]] = {
    case ContentType(MimeTypes.JSON) => jsonCodecs.decoders
    case ContentType(MimeTypes.XML)  => Xml.decoders
  }

  private val fallbackDecoder: PartialFunction[ContentType, CachedSchemaCompiler[BlobDecoder]] = {
    case ContentType(_) => stringAndBlobDecoder
  }

  private val predefinedEncoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobEncoder]] = {
    case ContentType(MimeTypes.JSON) => jsonCodecs.encoders
    case ContentType(MimeTypes.XML)  => Xml.encoders
  }

  private val fallbackEncoder: PartialFunction[ContentType, CachedSchemaCompiler[BlobEncoder]] = {
    case ContentType(_) => stringAndBlobEncoder
  }

  private def encoder(contentType: ContentType): CachedSchemaCompiler[BlobEncoder] =
    customEncoders
      .orElse(predefinedEncoders)
      .orElse(fallbackEncoder)(contentType)

  private def decoder(contentType: ContentType): CachedSchemaCompiler[BlobDecoder] =
    customDecoders
      .orElse(predefinedDecoders)
      .orElse(fallbackDecoder)(contentType)

  private def buildServerCodec(
    contentType: EndpointContentTypes,
    codecBuilder: HttpUnaryServerCodecs.Builder[ContextRoute, Request[RawBuffer], Result]
  ): UnaryServerCodecs.Make[ContextRoute, Request[RawBuffer], Result] =
    codecBuilder
      .withResponseMediaType(contentType.output.value)
      .withSuccessBodyEncoders(encoder(contentType.output))
      .withBodyDecoders(decoder(contentType.input))
      .withErrorBodyEncoders(encoder(contentType.error))
      .build()

  private def buildClientCodec[Request, Response](
    contentType: EndpointContentTypes,
    codecBuilder: HttpUnaryClientCodecs.Builder[RunnableClientRequest, Request, Response]
  ) =
    codecBuilder
      .withRequestMediaType(contentType.input.value)
      .withSuccessBodyDecoders(decoder(contentType.output))
      .withBodyEncoders(encoder(contentType.input))
      .withErrorBodyDecoders(decoder(contentType.error))

}
