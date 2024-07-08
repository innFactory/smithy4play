package de.innfactory.smithy4play.codecs

import de.innfactory.smithy4play.{ ContentType, Showable }

case class EndpointContentTypes(
  input: ContentType,
  output: ContentType,
  error: ContentType
) extends Showable
