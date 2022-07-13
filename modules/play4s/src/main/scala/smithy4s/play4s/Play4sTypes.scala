package main.scala.smithy4s.play4s

import cats.data.{EitherT, Kleisli}
import play.api.mvc.{RawBuffer, Request}
import smithy4s.Hints

import scala.concurrent.Future

case class BadRequest(
    message: String = "Entity or request malformed",
    additionalInfoToLog: Option[String] = None,
    additionalInfoErrorCode: Option[String] = None,
    statusCode: Int = 400
) extends MyErrorType

trait MyErrorType {
  def message: String
  def additionalInfoToLog: Option[String]
  def additionalInfoErrorCode: Option[String]
  def statusCode: Int
}

case class RoutingContext(hints: Hints, map: Map[String, Seq[String]])

object RoutingContext {
  def fromRequest(request: Request[RawBuffer], hints: Hints): RoutingContext =
    RoutingContext(hints, request.headers.toMap)
}

object MyMonads {

  type RouteResult[O] = EitherT[Future, MyErrorType, O]

  type ContextRoute[O] = Kleisli[RouteResult, RoutingContext, O]

}
