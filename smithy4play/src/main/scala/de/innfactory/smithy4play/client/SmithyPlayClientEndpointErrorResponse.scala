package de.innfactory.smithy4play.client

case class SmithyPlayClientEndpointErrorResponse(
  message: String,
  statusCode: Int,
  expectedStatusCode: Int
)
