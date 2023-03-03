package models

import play.api.libs.json.Json

case class TestJson(message: Option[String])

object TestJson {
  implicit val format = Json.format[TestJson]
}
