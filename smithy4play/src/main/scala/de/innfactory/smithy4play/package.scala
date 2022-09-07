package de.innfactory

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.client.{ SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse }
import org.slf4j
import play.api.Logger
import play.api.mvc.{ Headers, RequestHeader }
import smithy4s.http.{ CaseInsensitive, HttpEndpoint, PayloadError }

import scala.language.experimental.macros
import scala.annotation.{ compileTimeOnly, StaticAnnotation }
import scala.concurrent.Future

package object smithy4play {

  trait ContextRouteError {
    def message: String
    def additionalInfoToLog: Option[String]
    def additionalInfoErrorCode: Option[String]
    def statusCode: Int
  }

  type ClientResponse[O] = Future[Either[SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse[O]]]

  type RouteResult[O] = EitherT[Future, ContextRouteError, O]

  type ContextRoute[O] = Kleisli[RouteResult, RoutingContext, O]

  private[smithy4play] case class Smithy4PlayError(
    message: String,
    statusCode: Int
  ) extends ContextRouteError {
    override def additionalInfoToLog: Option[String] = None

    override def additionalInfoErrorCode: Option[String] = None
  }

  private[smithy4play] val logger: slf4j.Logger = Logger("smithy4play").logger

  private[smithy4play] def getHeaders(headers: Headers): Map[CaseInsensitive, Seq[String]] =
    headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  private[smithy4play] def matchRequestPath(
    x: RequestHeader,
    ep: HttpEndpoint[_]
  ): Option[Map[String, String]] =
    ep.matches(x.path.replaceFirst("/", "").split("/").filter(_.nonEmpty))

  @compileTimeOnly(
    "Macro failed to expand. \"Add: scalacOptions += \"-Ymacro-annotations\"\" to project settings"
  )
  class AutoRouting extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro AutoRoutingMacro.impl
  }

}
