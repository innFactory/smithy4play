package de.innfactory.smithy4play

import play.api.libs.json.Json

case class RoutingErrorResponse(message: String, errorCode: Option[String], additionalInformation: Option[String])

object RoutingErrorResponse {
  implicit val format = Json.format[RoutingErrorResponse]
}
