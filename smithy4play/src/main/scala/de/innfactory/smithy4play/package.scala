package de.innfactory

import alloy.SimpleRestJson
import cats.{ MonadError, MonadThrow }
import cats.data.{ EitherT, Kleisli }
import cats.effect.kernel.Resource.Pure
import cats.implicits.catsSyntaxEitherId
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.typesafe.config.Config
import de.innfactory.smithy4play.FutureThrowLike
import org.slf4j
import play.api.Logger
import play.api.http.MimeTypes
import play.api.libs.json.{ JsValue, Json, OFormat }
import play.api.mvc.{ Headers, RawBuffer, Request, RequestHeader }
import smithy4s.interopcats
import smithy4s.{ Blob, Hints }
import smithy4s.http.{
  CaseInsensitive,
  HttpEndpoint,
  HttpMethod,
  HttpRequest,
  HttpResponse,
  HttpUri,
  HttpUriScheme,
  Metadata
}

import scala.annotation.{ compileTimeOnly, StaticAnnotation }
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.experimental.macros
import scala.util.Try
import scala.util.matching.Regex
import scala.xml.Elem
import smithy4s.interopcats.monadThrowShim

package object smithy4play {

  trait ContextRouteError extends StatusResult[ContextRouteError] {
    def contentType: String = "application/json"

    def message: String

    def toXml: Elem = <ContextRouteError>
      <message>
        {message}
      </message>
    </ContextRouteError>

    def parse: String = contentType match {
      case "application/json" => toJson.toString()
      case "application/xml"  => toXml.toString()
    }

    def toJson: JsValue
  }

  case class ContentType(value: String)

  type RouteResult[O]           = EitherT[Future, ContextRouteError, O]
  type ContextRoute[O]          = Kleisli[RouteResult, RoutingContext, O]

  type MonadErrorResult[O] = EitherT[Future, Throwable, O]
  type FutureMonadError[O] = Kleisli[MonadErrorResult, RoutingContext, O]

  implicit def monadErrorConstructor(implicit ec: ExecutionContext): MonadThrow[FutureMonadError] =
    new MonadThrow[FutureMonadError] {

      override def raiseError[A](e: Throwable): FutureMonadError[A] = Future(
        e.asLeft[A]
      )

      override def handleErrorWith[A](
        fa: FutureMonadError[A]
      )(f: Throwable => FutureMonadError[A]): FutureMonadError[A] =
        fa.flatMap {
          case Left(value)  => f(value)
          case Right(value) => fa
        }

      override def pure[A](x: A): FutureMonadError[A] = Future(x.asRight)

      override def flatMap[A, B](fa: FutureMonadError[A])(f: A => FutureMonadError[B]): FutureMonadError[B] =
        fa.flatMap {
          case Left(value)  => Future(value.asLeft[B])
          case Right(value) => f(value)
        }

      override def tailRecM[A, B](a: A)(f: A => FutureMonadError[Either[A, B]]): FutureMonadError[B] = {
        val funcResult: FutureMonadError[Either[A, B]] = f(a)
        funcResult.flatMap {
          case Left(value)  => Future(value.asLeft[B])
          case Right(value) =>
            value match {
              case Left(value)  => tailRecM(value)(f)
              case Right(value) => Future(value.asRight)
            }
        }
      }
    }

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

  implicit class EnhancedHints(hints: Hints) {
    def toMimeType: String =
      (Option.empty, hints.get(SimpleRestJson.getTag)) match {
        case (Some(_), None) => MimeTypes.XML
        case _               => MimeTypes.JSON
      }
  }

  implicit class EnhancedReaderConfig(readerConfig: ReaderConfig) {

    def fromApplicationConfig(config: Config): ReaderConfig = {
      val maxCharBufSize                     =
        Try(config.getInt("smithy4play.jsoniter.maxCharBufSize")).toOption
      val preferredBufSize                   =
        Try(config.getInt("smithy4play.jsoniter.preferredBufSize")).toOption
      val preferredCharBufSize               =
        Try(config.getInt("smithy4play.jsoniter.preferredCharBufSize")).toOption
      val hexDumpSize                        =
        Try(config.getInt("smithy4play.jsoniter.hexDumpSize")).toOption
      val maxBufSize                         =
        Try(config.getInt("smithy4play.jsoniter.MaxBufSize")).toOption
      val throwReaderExceptionWithStackTrace =
        Try(config.getBoolean("smithy4play.jsoniter.throwReaderExceptionWithStackTrace")).toOption
      val appendHexDumpToParseException      =
        Try(config.getBoolean("smithy4play.jsoniter.appendHexDumpToParseException")).toOption
      val checkForEndOfInput                 =
        Try(config.getBoolean("smithy4play.jsoniter.checkForEndOfInput")).toOption

      readerConfig
        .withMaxCharBufSize(maxCharBufSize.getOrElse(readerConfig.maxCharBufSize))
        .withPreferredBufSize(preferredBufSize.getOrElse(readerConfig.preferredBufSize))
        .withCheckForEndOfInput(checkForEndOfInput.getOrElse(readerConfig.checkForEndOfInput))
        .withPreferredCharBufSize(preferredCharBufSize.getOrElse(readerConfig.preferredCharBufSize))
        .withHexDumpSize(hexDumpSize.getOrElse(readerConfig.hexDumpSize))
        .withMaxBufSize(maxBufSize.getOrElse(readerConfig.maxBufSize))
        .withAppendHexDumpToParseException(
          appendHexDumpToParseException.getOrElse(readerConfig.appendHexDumpToParseException)
        )
        .withThrowReaderExceptionWithStackTrace(
          throwReaderExceptionWithStackTrace.getOrElse(readerConfig.throwReaderExceptionWithStackTrace)
        )
    }
  }

  implicit class EnhancedThrowable(throwable: Throwable) {
    private val regex1: Regex = """(?s), offset: (?:0x)?[0-9a-fA-F]+, buf:.*""".r
    private val regex2: Regex = """(.*), offset: .*, buf:.* (\(path:.*\))""".r

    def filterMessage: String =
      regex2.replaceAllIn(
        throwable.getMessage.filter(_ >= ' '),
        m =>
          m.matched match {
            case regex2(initialMessage, endMessage) => s"$initialMessage: $endMessage"
            case msg                                => regex1.replaceAllIn(msg, "")
          }
      )
  }

  private[smithy4play] case class Smithy4PlayError(
    message: String,
    status: Status,
    additionalInformation: Option[String] = None,
    override val contentType: String
  ) extends ContextRouteError {
    override def toJson: JsValue = Json.toJson(this)(Smithy4PlayError.format)

    override def addHeaders(headers: Map[String, String]): Smithy4PlayError = this.copy(
      status = status.copy(
        headers = status.headers ++ headers
      )
    )
  }

  object Smithy4PlayError {
    implicit val format: OFormat[Smithy4PlayError] = Json.format[Smithy4PlayError]
  }

  private[smithy4play] val logger: slf4j.Logger = Logger("smithy4play").logger

  private[smithy4play] def getHeaders(headers: Headers): Map[CaseInsensitive, Seq[String]] =
    headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  private[smithy4play] def matchRequestPath(
    requestHeader: RequestHeader,
    ep: HttpEndpoint[?]
  ): Option[Map[String, String]] =
    ep.matches(deconstructPath(requestHeader.path))

  private[smithy4play] def deconstructPath(path: String) =
    path.replaceFirst("/", "").split("/").filter(_.nonEmpty)


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

  type EitherThrowLike[E, O] = Either[E, O]
  type FutureThrowLike[E, O] = Future[EitherThrowLike[E, O]]

  def toSmithy4sHttpRequest(
    request: Request[RawBuffer]
  )(implicit ec: ExecutionContext): FutureMonadError[HttpRequest[Blob]] =
    Future {
      val pathParams = deconstructPath(request.path)
      val uri        = toSmithy4sHttpUri(pathParams, request.secure, request.host, request.queryString)
      val headers    = getHeaders(request.headers)
      val method     = getSmithy4sHttpMethod(request.method)
      val parsedBody = request.body.asBytes().map(b => Blob(b.toByteBuffer)).getOrElse(Blob.empty)
      HttpRequest(method, uri, headers, parsedBody).asRight[Throwable]
    }

  def toSmithy4sHttpUri(
    path: IndexedSeq[String],
    secure: Boolean,
    host: String,
    queryString: Map[String, Seq[String]]
  ): HttpUri = {
    val uriScheme = if (secure) HttpUriScheme.Https else HttpUriScheme.Http

    HttpUri(
      uriScheme,
      host,
      None,
      path,
      queryString,
      None
    )
  }

  def getQueryParams(requestHeader: RequestHeader): Map[String, Seq[String]] = requestHeader.queryString

  private[smithy4play] def getSmithy4sHttpMethod(method: String): HttpMethod =
    HttpMethod.fromStringOrDefault(method.toUpperCase)

}
