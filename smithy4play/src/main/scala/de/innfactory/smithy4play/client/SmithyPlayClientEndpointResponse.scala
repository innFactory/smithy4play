package de.innfactory.smithy4play.client

case class SmithyPlayClientEndpointResponse[O](
  body: Option[O],
  headers: Map[String, Seq[String]],
  statusCode: Int,
  expectedStatusCode: Int
)
