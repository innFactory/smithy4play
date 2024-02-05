package de.innfactory

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.client.SmithyPlayClientEndpointErrorResponse
import org.slf4j
import play.api.Logger
import play.api.libs.json.{ JsValue, Json, OFormat }
import play.api.mvc.{ Headers, RequestHeader }
import smithy4s.Blob
import smithy4s.http.{ CaseInsensitive, HttpEndpoint, HttpResponse, Metadata }

import scala.annotation.{ compileTimeOnly, StaticAnnotation }
import scala.concurrent.Future
import scala.language.experimental.macros

package object smithy4play {

  trait ContextRouteError extends StatusResult[ContextRouteError] {
    def message: String
    def toJson: JsValue
  }

  type ClientResponse[O]        = Future[Either[SmithyPlayClientEndpointErrorResponse, HttpResponse[O]]]
  type RunnableClientRequest[O] = Kleisli[ClientResponse, Option[Map[CaseInsensitive, Seq[String]]], O]
  type RouteResult[O]           = EitherT[Future, ContextRouteError, O]
  type ContextRoute[O]          = Kleisli[RouteResult, RoutingContext, O]

  trait StatusResult[S <: StatusResult[S]] {
    def status: Status
    def addHeaders(headers: Map[String, String]): S
  }

  case class Status(headers: Map[String, String], statusCode: Int)
  object Status {
    implicit val format: OFormat[Status] = Json.format[Status]
  }

  type EndpointRequest = PlayHttpRequest[Blob]
  case class PlayHttpRequest[Body](body: Body, metadata: Metadata) {
    def addHeaders(headers: Map[CaseInsensitive, Seq[String]]): PlayHttpRequest[Body] = this.copy(
      metadata = metadata.copy(
        headers = metadata.headers ++ headers
      )
    )
  }

  private[smithy4play] case class Smithy4PlayError(
    message: String,
    status: Status,
    additionalInformation: Option[String] = None
  ) extends ContextRouteError {
    override def toJson: JsValue                                            = Json.toJson(this)(Smithy4PlayError.format)
    override def addHeaders(headers: Map[String, String]): Smithy4PlayError = this.copy(
      status = status.copy(
        headers = status.headers ++ headers
      )
    )
  }

  object Smithy4PlayError {
    implicit val format = Json.format[Smithy4PlayError]
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

  private[smithy4play] trait Showable {
    this: Product =>
    override def toString: String = this.show
  }

  private[smithy4play] object Showable {
    implicit class ShowableProduct(product: Product) {
      def show: String = {
        val className   = product.productPrefix
        val fieldNames  = product.productElementNames.toList
        val fieldValues = product.productIterator.toList
        val fields      = fieldNames.zip(fieldValues).map { case (name, value) =>
          value match {
            case subProduct: Product => s"$name = ${subProduct.show}"
            case _                   => s"$name = $value"
          }
        }
        fields.mkString(s"$className(", ", ", ")")
      }
    }
  }

}
