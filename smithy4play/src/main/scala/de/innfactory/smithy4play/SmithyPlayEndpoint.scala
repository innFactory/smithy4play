package de.innfactory.smithy4play

import akka.util.ByteString
import cats.data.{ EitherT, Kleisli, Validated }
import cats.implicits.toBifunctorOps
import play.api.mvc.{
  AbstractController,
  ControllerComponents,
  Handler,
  RawBuffer,
  Request,
  RequestHeader,
  Result,
  Results
}
import smithy4s.{ ByteArray, Endpoint, Service }
import smithy4s.http.{ CaseInsensitive, CodecAPI, HttpEndpoint, Metadata, PathParams }
import smithy4s.schema.Schema
import smithy.api.{ Auth, HttpBearerAuth }
import smithy4s.kinds.FunctorInterpreter

import scala.concurrent.{ ExecutionContext, Future }

class SmithyPlayEndpoint[Alg[_[_, _, _, _, _]], F[_] <: ContextRoute[_], Op[
  _,
  _,
  _,
  _,
  _
], I, E, O, SI, SO](
  service: Service[Alg],
  impl: FunctorInterpreter[Op, F],
  middleware: Seq[MiddlewareBase],
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  codecs: CodecAPI
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends AbstractController(cc) {

  private val httpEndpoint: Either[HttpEndpoint.HttpEndpointError, HttpEndpoint[I]] = HttpEndpoint.cast(endpoint)
  private val serviceHints                                                          = service.hints
  private val endpointHints                                                         = endpoint.hints

  private val inputSchema: Schema[I]  = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataDecoder: Metadata.PartialDecoder[I] =
    Metadata.PartialDecoder.fromSchema(inputSchema)

  private val outputMetadataEncoder: Metadata.Encoder[O] =
    Metadata.Encoder.fromSchema(outputSchema)

  def handler(v1: RequestHeader): Handler =
    httpEndpoint.map { httpEp =>
      Action.async(parse.raw) { implicit request =>
        if (request.body.size > 0 && request.body.asBytes().isEmpty) {
          logger.error(
            "received body size does not equal the parsed body size. \n" +
              "This is probably due to the body being too large and thus play is unable to parse.\n" +
              "Try setting play.http.parser.maxMemoryBuffer in application.conf"
          )
        }
        val initialKleisli     = Kleisli[RouteResult, RoutingContext, RoutingContext](EitherT.rightT(_))
        val chainedMiddlewares = middleware.map(_.middleware).foldLeft(initialKleisli)((a, b) => a.andThen(b))
        val result             = for {
          contextFromMid <- chainedMiddlewares.run(RoutingContext.fromRequest(request, serviceHints, endpointHints))
          pathParams     <- getPathParams(v1, httpEp)
          metadata        = getMetadata(pathParams, v1)
          input          <- getInput(request, metadata)
          _              <- EitherT(
                              Future(
                                Validated
                                  .cond(validateAuthHints(metadata), (), Smithy4PlayError("Unauthorized", 401))
                                  .toEither
                              )
                            )
          endpointLogic   = impl(endpoint.wrap(input)).asInstanceOf[Kleisli[RouteResult, RoutingContext, O]]
          res            <- endpointLogic.run(contextFromMid)
        } yield res
        result.value.map {
          case Left(value)  => handleFailure(value)
          case Right(value) => handleSuccess(value, httpEp.code)
        }
      }
    }
      .getOrElse(Action(NotFound("404")))

  private def validateAuthHints(metadata: Metadata) = {
    val serviceAuthHints = serviceHints.get(HttpBearerAuth.tagInstance).map(_ => Auth(Set(HttpBearerAuth.id.show)))
    for {
      authSet <- endpoint.hints.get(Auth.tag) orElse serviceAuthHints
      _       <- authSet.value.find(_.value == HttpBearerAuth.id.show)
    } yield metadata.headers.contains(CaseInsensitive("Authorization"))
  }.getOrElse(true)

  private def getPathParams(
    v1: RequestHeader,
    httpEp: HttpEndpoint[I]
  ): EitherT[Future, ContextRouteError, Map[String, String]] =
    EitherT(
      Future(
        matchRequestPath(v1, httpEp)
          .toRight[ContextRouteError](
            Smithy4PlayError(
              "Error in extracting PathParams",
              400
            )
          )
      )
    )

  private def getInput(
    request: Request[RawBuffer],
    metadata: Metadata
  ): EitherT[Future, ContextRouteError, I] =
    EitherT(
      Future(inputMetadataDecoder.total match {
        case Some(value) =>
          value
            .decode(metadata)
            .leftMap { e =>
              logger.info(e.getMessage())
              Smithy4PlayError(
                "Error decoding Input",
                500
              )
            }
        case None        =>
          request.contentType.getOrElse("application/json") match {
            case "application/json" => parseJson(request, metadata)
            case _                  => parseRaw(request, metadata)
          }

      })
    )

  private def parseJson(request: Request[RawBuffer], metadata: Metadata): Either[ContextRouteError, I] = {
    val codec = codecs.compileCodec(inputSchema)
    for {
      metadataPartial <- inputMetadataDecoder
                           .decode(metadata)
                           .leftMap { e =>
                             logger.info(e.getMessage())
                             Smithy4PlayError(
                               "Error decoding Input Metadata",
                               500
                             )
                           }
      c               <-
        codecs
          .decodeFromByteBufferPartial(
            codec,
            request.body.asBytes().getOrElse(ByteString.empty).toByteBuffer
          )
          .leftMap(e => Smithy4PlayError(s"expected: ${e.expected}", 400, additionalInformation = Some(e.getMessage())))
    } yield metadataPartial.combine(c)
  }

  private def parseRaw(request: Request[RawBuffer], metadata: Metadata): Either[ContextRouteError, I] = {
    val nativeCodec: CodecAPI = CodecAPI.nativeStringsAndBlob(codecs)
    val input                 = ByteArray(request.body.asBytes().getOrElse(ByteString.empty).toArray)
    val codec                 = nativeCodec
      .compileCodec(inputSchema)
    for {
      metadataPartial <- inputMetadataDecoder
                           .decode(metadata)
                           .leftMap { e =>
                             logger.info(e.getMessage())
                             Smithy4PlayError(
                               "Error decoding Input Metadata",
                               500,
                               additionalInformation = Some(e.getMessage())
                             )
                           }
      bodyPartial     <-
        nativeCodec
          .decodeFromByteArrayPartial(codec, input.array)
          .leftMap(e => Smithy4PlayError(s"expected: ${e.expected}", 400, additionalInformation = Some(e.getMessage())))
    } yield metadataPartial.combine(bodyPartial)
  }

  private def getMetadata(pathParams: PathParams, request: RequestHeader): Metadata =
    Metadata(
      path = pathParams,
      headers = getHeaders(request.headers),
      query = request.queryString.map { case (k, v) => (k.trim, v) }
    )

  def handleFailure(error: ContextRouteError): Result =
    Results.Status(error.statusCode)(error.toJson)

  private def handleSuccess(output: O, code: Int): Result = {
    val outputMetadata                  = outputMetadataEncoder.encode(output)
    val outputHeaders                   = outputMetadata.headers.map { case (k, v) =>
      (k.toString.toLowerCase, v.mkString(""))
    }
    val contentType                     =
      outputHeaders.getOrElse("content-type", "application/json")
    val outputHeadersWithoutContentType = outputHeaders.-("content-type").toList
    val codecApi                        = contentType match {
      case "application/json" => codecs
      case _                  => CodecAPI.nativeStringsAndBlob(codecs)
    }
    logger.debug(s"[SmithyPlayEndpoint] Headers: ${outputHeaders.mkString("|")}")

    val status     = Results.Status(code)
    val codec      = codecApi.compileCodec(outputSchema)
    val expectBody = Metadata.PartialDecoder
      .fromSchema(outputSchema)
      .total
      .isEmpty // expect body if metadata decoder is not total
    if (expectBody) {
      status(codecApi.writeToArray(codec, output))
        .as(contentType)
        .withHeaders(outputHeadersWithoutContentType: _*)
    } else status("")

  }

}
