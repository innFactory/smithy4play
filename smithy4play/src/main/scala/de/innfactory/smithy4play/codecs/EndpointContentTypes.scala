package de.innfactory.smithy4play.codecs

import de.innfactory.smithy4play.{ ContentType, Showable }
import play.api.http.MimeTypes

case class EndpointContentTypes(
  input: ContentType,
  output: ContentType,
  error: ContentType
) extends Showable

object EndpointContentTypes {

  val JsonOnly: EndpointContentTypes = EndpointContentTypes(
    input = ContentType(MimeTypes.JSON),
    output = ContentType(MimeTypes.JSON),
    error = ContentType(MimeTypes.JSON)
  )

  val XmlOnly: EndpointContentTypes = EndpointContentTypes(
    input = ContentType(MimeTypes.XML),
    output = ContentType(MimeTypes.XML),
    error = ContentType(MimeTypes.XML)
  )

  def isJsonOnly(ct: EndpointContentTypes): Boolean =
    ct.input.value == MimeTypes.JSON &&
      ct.output.value == MimeTypes.JSON &&
      ct.error.value == MimeTypes.JSON
}
