package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.Showable

case class SmithyPlayClientEndpointErrorResponse(
  error: Array[Byte],
  statusCode: Int,
  expectedStatusCode: Int
) extends Showable
