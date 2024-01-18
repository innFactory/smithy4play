package de.innfactory.smithy4play

import smithy4s.codecs.{ PayloadDecoder, PayloadEncoder }
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.xml.Xml

object CodecDecider {

  def encoder(
    contentType: Seq[String]
  )(implicit encoder: CachedSchemaCompiler[PayloadEncoder]): CachedSchemaCompiler[PayloadEncoder] =
    contentType match {
      case Seq("application/json") => encoder
      case Seq("application/xml")  => Xml.encoders
      case _                       =>
        CachedSchemaCompiler
          .getOrElse(smithy4s.codecs.StringAndBlobCodecs.encoders, encoder)
    }

  def decoder(
    contentType: Seq[String]
  )(implicit decoder: CachedSchemaCompiler[PayloadDecoder]): CachedSchemaCompiler[PayloadDecoder] =
    contentType match {
      case Seq("application/json") => decoder
      case Seq("application/xml")  => Xml.decoders
      case _                       =>
        CachedSchemaCompiler
          .getOrElse(smithy4s.codecs.StringAndBlobCodecs.decoders, decoder)
    }

}
