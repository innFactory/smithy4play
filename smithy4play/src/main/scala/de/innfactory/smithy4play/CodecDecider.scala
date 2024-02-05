package de.innfactory.smithy4play

import smithy4s.capability.instances.either._
import smithy4s.codecs.Writer.CachedCompiler
import smithy4s.codecs._
import smithy4s.http.{HttpResponse, HttpRestSchema, Metadata, MetadataError}
import smithy4s.json.Json
import smithy4s.kinds.PolyFunction
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.xml.Xml
import smithy4s.{Blob, codecs}

object CodecDecider {

  private val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(
      Json.jsoniter
    )

  private val jsonEncoder: BlobEncoder.Compiler                       = jsonCodecs.encoders
  private val jsonDecoder: BlobDecoder.Compiler                       = jsonCodecs.decoders
  private val metadataEncoder                                         = Metadata.Encoder
  private val metadataDecoder: CachedSchemaCompiler[Metadata.Decoder] = Metadata.Decoder

  def encoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[codecs.BlobEncoder] =
    contentType match {
      case Seq("application/json") => jsonEncoder
      case Seq("application/xml")  => Xml.encoders
      case _                       =>
        CachedSchemaCompiler
          .getOrElse(smithy4s.codecs.StringAndBlobCodecs.encoders, jsonEncoder)
    }

  def requestDecoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[Decoder[Either[Throwable, *], PlayHttpRequest[Blob], *]] =
    HttpRestSchema.combineDecoderCompilers[Either[Throwable, *], PlayHttpRequest[Blob]](
      metadataDecoder
        .mapK(
          Decoder.in[Either[MetadataError, *]].composeK[Metadata, PlayHttpRequest[Blob]](_.metadata)
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, *], PlayHttpRequest[Blob], *]]],
      decoder(contentType)
        .mapK(
          Decoder.in[Either[PayloadError, *]].composeK[Blob, PlayHttpRequest[Blob]](_.body)
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, *], PlayHttpRequest[Blob], *]]],
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
      _ => true
    )

  def httpResponseDecoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[Decoder[Either[Throwable, *], HttpResponse[Blob], *]] =
    HttpRestSchema.combineDecoderCompilers[Either[Throwable, *], HttpResponse[Blob]](
      metadataDecoder
        .mapK(
          Decoder
            .in[Either[MetadataError, *]]
            .composeK[Metadata, HttpResponse[Blob]](r =>
              Metadata(Map.empty, Map.empty, headers = r.headers, statusCode = Some(r.statusCode))
            )
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, *], HttpResponse[Blob], *]]],
      decoder(contentType)
        .mapK(
          Decoder.in[Either[PayloadError, *]].composeK[Blob, HttpResponse[Blob]](_.body)
        )
        .asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, *], HttpResponse[Blob], *]]],
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
      _ => true
    )

  private val httpRequestBodyLift: Writer[HttpResponse[Blob], Blob]                                      =
    Writer.lift[HttpResponse[Blob], Blob]((res, blob) => res.copy(body = blob))
  private val httpRequestMetadataLift: Writer[HttpResponse[Blob], Metadata]                              =
    Writer.lift[HttpResponse[Blob], Metadata]((res, metadata) =>
      res.addHeaders(metadata.headers.map { case (insensitive, value) =>
        (insensitive, value)
      })
    )
  private val httpRequestBlobPipe: PolyFunction[Encoder[Blob, *], Writer[HttpResponse[Blob], *]]         =
    smithy4s.codecs.Encoder.pipeToWriterK[HttpResponse[Blob], Blob](httpRequestBodyLift)
  private val httpRequestMetadataPipe: PolyFunction[Encoder[Metadata, *], Writer[HttpResponse[Blob], *]] =
    smithy4s.codecs.Encoder.pipeToWriterK(httpRequestMetadataLift)

  private val blobLift: Writer[EndpointRequest, Blob]                                      =
    Writer.lift[EndpointRequest, Blob]((res, blob) => res.copy(body = blob))
  private val metadataLift: Writer[EndpointRequest, Metadata]                              =
    Writer.lift[EndpointRequest, Metadata]((res, metadata) =>
      res.addHeaders(metadata.headers.map { case (insensitive, value) =>
        (insensitive, value)
      })
    )
  private val blobPipe: PolyFunction[Encoder[Blob, *], Writer[EndpointRequest, *]]         =
    smithy4s.codecs.Encoder.pipeToWriterK[EndpointRequest, Blob](blobLift)
  private val metadataPipe: PolyFunction[Encoder[Metadata, *], Writer[EndpointRequest, *]] =
    smithy4s.codecs.Encoder.pipeToWriterK(metadataLift)

  def decoder(
    contentType: Seq[String]
  ): CachedSchemaCompiler[BlobDecoder] =
    contentType match {
      case Seq("application/json") => jsonDecoder
      case Seq("application/xml")  => Xml.decoders
      case _                       =>
        CachedSchemaCompiler
          .getOrElse(smithy4s.codecs.StringAndBlobCodecs.decoders, jsonDecoder)
    }

}
