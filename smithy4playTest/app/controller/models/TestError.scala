package controller.models

import de.innfactory.smithy4play.ContextRouteError
import play.api.libs.json.{ JsValue, Json }

case class TestError(
  message: String
) extends ContextRouteError {
  override def statusCode: Int = 500
  override def toJson: JsValue = Json.toJson(this)(TestError.format)
}

object TestError {
  implicit val format = Json.format[TestError]
}
