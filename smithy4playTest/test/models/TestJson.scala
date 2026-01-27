package models

import play.api.libs.json.{ Json, OFormat }

case class TestJson(message: Option[String])

object TestJson {
  implicit val format: OFormat[TestJson] = Json.format[TestJson]
}
