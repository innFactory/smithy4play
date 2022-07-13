package main.scala.smithy4s.play4s

import play.api.mvc.{RawBuffer, Request}
import smithy4s.Hints

case class BadRequest(
    message: String = "Entity or request malformed",
    additionalInfoToLog: Option[String] = None,
    additionalInfoErrorCode: Option[String] = None,
    statusCode: Int = 400
) extends ContextRouteError

trait ContextRouteError {
  def message: String
  def additionalInfoToLog: Option[String]
  def additionalInfoErrorCode: Option[String]
  def statusCode: Int
}

case class RoutingContext(map: Map[String, Seq[String]])

object RoutingContext {
  def fromRequest(request: Request[RawBuffer]): RoutingContext =
    RoutingContext(request.headers.toMap)
}
