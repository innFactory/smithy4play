package de.innfactory.play4s

import play.api.libs.json.Json

case class RoutingErrorResponse(message: String, errorCode: Int)

object RoutingErrorResponse {
  implicit val writes = Json.writes[RoutingErrorResponse]
}
