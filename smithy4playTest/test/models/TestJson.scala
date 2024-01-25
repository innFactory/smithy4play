package models

import play.api.libs.json.{Json, Reads, Writes}

case class TestJson(message: Option[String])

object TestJson {
  given Writes[TestJson] = Json.format[TestJson]
  given Reads[TestJson] = Json.reads[TestJson]
}
