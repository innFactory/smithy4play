package de.innfactory.smithy4play.client

case class SmithyPlayClientEndpointErrorResponse(
  error: Array[Byte],
  statusCode: Int,
  expectedStatusCode: Int
)
