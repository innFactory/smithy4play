package controller.models

import de.innfactory.smithy4play.{ContextRouteError, Status}
import play.api.libs.json.{Format, JsValue, Json}

case class TestError(
  message: String,
  status: Status = Status(Map.empty, 500)
) extends ContextRouteError {
  override def toJson: JsValue                                     = Json.toJson(this)
  override def addHeaders(headers: Map[String, String]): TestError = this.copy(
    status = status.copy(
      headers = status.headers ++ headers
    )
  )
}

object TestError {
  given Format[TestError] = Json.format[TestError]
}
