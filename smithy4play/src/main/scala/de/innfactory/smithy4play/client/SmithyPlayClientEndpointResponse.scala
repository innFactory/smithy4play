package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.Showable

case class SmithyPlayClientEndpointResponse[O](
  body: Option[O],
  headers: Map[String, Seq[String]],
  statusCode: Int
) extends Showable
