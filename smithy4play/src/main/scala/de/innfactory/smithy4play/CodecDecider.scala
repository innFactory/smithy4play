package de.innfactory.smithy4play

import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import play.api.http.MimeTypes
import smithy4s.capability.instances.either._
import smithy4s.codecs.Writer.CachedCompiler
import smithy4s.codecs._
import smithy4s.http.{ HttpResponse, HttpRestSchema, Metadata, MetadataError }
import smithy4s.json.Json
import smithy4s.kinds.PolyFunction
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.xml.Xml
import smithy4s.{ codecs, Blob }

case class CodecDecider(readerConfig: ReaderConfig) {

  private val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(
      Json.jsoniter
    )
    .withJsoniterReaderConfig(readerConfig)

  private val jsonEncoder: BlobEncoder.Compiler                       = jsonCodecs.encoders
  private val jsonDecoder: BlobDecoder.Compiler                       = jsonCodecs.decoders
  private val metadataEncoder                                         = Metadata.Encoder
  private val metadataDecoder: CachedSchemaCompiler[Metadata.Decoder] = Metadata.Decoder

  def encoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[codecs.BlobEncoder] =
    contentType.map(_.split(";").head) match {
      case Seq(MimeTypes.JSON) => jsonEncoder
      case Seq(MimeTypes.XML)  => Xml.encoders
      case _                   =>
        CachedSchemaCompiler
          .getOrElse(smithy4s.codecs.StringAndBlobCodecs.encoders, jsonEncoder)
    }

  def requestDecoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[Decoder[Either[Throwable, _], PlayHttpRequest[Blob], _]] =
    HttpRestSchema.combineDecoderCompilers[Either[Throwable, _], PlayHttpRequest[Blob]](
      metadataDecoder
        .mapK(
          Decoder.in[Either[MetadataError, _]].composeK[Metadata, PlayHttpRequest[Blob]](_.metadata)
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, _], PlayHttpRequest[Blob], _]]],
      decoder(contentType)
        .mapK(
          Decoder.in[Either[PayloadError, _]].composeK[Blob, PlayHttpRequest[Blob]](_.body)
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, _], PlayHttpRequest[Blob], _]]],
      _ => Right(())
    )(eitherZipper)

  def requestEncoder(
    contentType: Seq[String]
  ): CachedCompiler[EndpointRequest] =
    HttpRestSchema.combineWriterCompilers(
      metadataEncoder.mapK(
        metadataPipe
      ),
      encoder(contentType).mapK(
        blobPipe
      ),
      _ => false
    )

  def httpResponseDecoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[Decoder[Either[Throwable, _], HttpResponse[Blob], _]] =
    HttpRestSchema.combineDecoderCompilers[Either[Throwable, _], HttpResponse[Blob]](
      metadataDecoder
        .mapK(
          Decoder
            .in[Either[MetadataError, _]]
            .composeK[Metadata, HttpResponse[Blob]](r =>
              Metadata(Map.empty, Map.empty, headers = r.headers, statusCode = Some(r.statusCode))
            )
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, _], HttpResponse[Blob], _]]],
      decoder(contentType)
        .mapK(
          Decoder.in[Either[PayloadError, _]].composeK[Blob, HttpResponse[Blob]](_.body)
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, _], HttpResponse[Blob], _]]],
      _ => Right(())
    )(eitherZipper)

  def httpMessageEncoder(
    contentType: Seq[String]
  ): CachedCompiler[HttpResponse[Blob]] =
    HttpRestSchema.combineWriterCompilers(
      metadataEncoder.mapK(
        httpRequestMetadataPipe
      ),
      encoder(contentType).mapK(
        httpRequestBlobPipe
      ),
      _ => false
    )

  private val httpRequestBodyLift: Writer[HttpResponse[Blob], Blob]                                      =
    Writer.lift[HttpResponse[Blob], Blob]((res, blob) => res.copy(body = blob))
  private val httpRequestMetadataLift: Writer[HttpResponse[Blob], Metadata]                              =
    Writer.lift[HttpResponse[Blob], Metadata]((res, metadata) =>
      res.addHeaders(metadata.headers.map { case (insensitive, value) =>
        (insensitive, value)
      })
    )
  private val httpRequestBlobPipe: PolyFunction[Encoder[Blob, _], Writer[HttpResponse[Blob], _]]         =
    smithy4s.codecs.Encoder.pipeToWriterK[HttpResponse[Blob], Blob](httpRequestBodyLift)
  private val httpRequestMetadataPipe: PolyFunction[Encoder[Metadata, _], Writer[HttpResponse[Blob], _]] =
    smithy4s.codecs.Encoder.pipeToWriterK(httpRequestMetadataLift)

  private val blobLift: Writer[EndpointRequest, Blob]                                      =
    Writer.lift[EndpointRequest, Blob]((res, blob) => res.copy(body = blob))
  private val metadataLift: Writer[EndpointRequest, Metadata]                              =
    Writer.lift[EndpointRequest, Metadata]((res, metadata) =>
      res.addHeaders(metadata.headers.map { case (insensitive, value) =>
        (insensitive, value)
      })
    )
  private val blobPipe: PolyFunction[Encoder[Blob, _], Writer[EndpointRequest, _]]         =
    smithy4s.codecs.Encoder.pipeToWriterK[EndpointRequest, Blob](blobLift)
  private val metadataPipe: PolyFunction[Encoder[Metadata, _], Writer[EndpointRequest, _]] =
    smithy4s.codecs.Encoder.pipeToWriterK(metadataLift)

  def decoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[BlobDecoder] =
    contentType.map(_.split(";").head) match {
      case Seq(MimeTypes.JSON) => jsonDecoder
      case Seq(MimeTypes.XML)  => Xml.decoders
      case _                   =>
        CachedSchemaCompiler
          .getOrElse(smithy4s.codecs.StringAndBlobCodecs.decoders, jsonDecoder)
    }

}
