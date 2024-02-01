package de.innfactory.smithy4play

import cats.implicits.catsSyntaxEitherId
import smithy4s.capability.{ MonadThrowLike, Zipper }
import smithy4s.capability.instances.either._
import smithy4s.capability.instances.option._
import smithy4s.codecs.Writer.CachedCompiler
import smithy4s.codecs._
import smithy4s.http.{ HttpContractError, HttpRequest, HttpResponse, HttpRestSchema, Metadata, MetadataError }
import smithy4s.json.Json
import smithy4s.kinds.PolyFunction
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.xml.Xml
import smithy4s.{ codecs, Blob, PartialData, Schema }

object CodecDecider {

  private val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(
      Json.jsoniter
    )

  private val blobLift: Writer[Blob, Blob] = Writer.lift[Blob, Blob]((_, blob) => blob)
  private val blobPipe                     = smithy4s.codecs.Encoder.pipeToWriterK[Blob, Blob](blobLift)

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

  /*def encoderWithMetadata(
    contentType: Seq[String]
  ): CachedCompiler[Blob] = HttpRestSchema.combineWriterCompilers(
    metadataEncoder.mapK(blobPipe),
    encoder(contentType).mapK(blobPipe),
    _ => true
  )*/
  def decoderWithMetadata(
    contentType: Seq[String]
  ): CachedSchemaCompiler[Decoder[Either[Throwable, *], PlayHttpRequest[Blob], *]] =
    HttpRestSchema.combineDecoderCompilers[Either[Throwable, *], PlayHttpRequest[Blob]](
      metadataDecoder.mapK(
        Decoder.in[Either[MetadataError, *]].composeK[Metadata, PlayHttpRequest[Blob]](_.metadata)
      ).asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, *], PlayHttpRequest[Blob], *]]],
      decoder(contentType).mapK(
        Decoder.in[Either[PayloadError, *]].composeK[Blob, PlayHttpRequest[Blob]](_.body)
      ).asInstanceOf[CachedSchemaCompiler[Decoder[Either[Throwable, *], PlayHttpRequest[Blob], *]]],
      _ => Right(())
    )(eitherZipper)
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
