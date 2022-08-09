package de.innfactory

import cats.data.{EitherT, Kleisli}
import org.slf4j
import play.api.Logger
import play.api.mvc.RequestHeader
import smithy4s.http.{CaseInsensitive, HttpEndpoint}
import scala.language.experimental.macros
import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.concurrent.Future

package object smithy4play {

  type RouteResult[O] = EitherT[Future, ContextRouteError, O]

  type ContextRoute[O] = Kleisli[RouteResult, RoutingContext, O]

  trait ContextRouteError {
    def message: String
    def additionalInfoToLog: Option[String]
    def additionalInfoErrorCode: Option[String]
    def statusCode: Int
  }

  private[smithy4play] case class Smithy4PlayError(
      message: String,
      statusCode: Int
  ) extends ContextRouteError {
    override def additionalInfoToLog: Option[String] = None

    override def additionalInfoErrorCode: Option[String] = None
  }

  //change name of logger
  private[smithy4play] val logger: slf4j.Logger = Logger("smithy4play").logger

  private[smithy4play] def getHeaders(req: RequestHeader): Map[CaseInsensitive, Seq[String]] =
    req.headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  private[smithy4play] def matchRequestPath(
      x: RequestHeader,
      ep: HttpEndpoint[_]
  ): Option[Map[String, String]] = {
    ep.matches(x.path.replaceFirst("/", "").split("/").filter(_.nonEmpty))
  }

  @compileTimeOnly(
    "Macro failed to expand. \"Add: scalacOptions += \"-Ymacro-annotations\"\" to project settings"
  )
  class AutoRouting extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro AutoRoutingMacro.impl
  }

}
