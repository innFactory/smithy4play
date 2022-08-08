package de.innfactory.smithy4play

import play.api.libs.json.Json

case class RoutingErrorResponse(message: String, errorCode: Option[String] = None)

object RoutingErrorResponse {
  implicit val writes = Json.writes[RoutingErrorResponse]
}
