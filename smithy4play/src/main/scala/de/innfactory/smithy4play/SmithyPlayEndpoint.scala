package de.innfactory.smithy4play

import cats.data.EitherT
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
import smithy4s.{ ByteArray, Endpoint, Interpreter }
import smithy4s.http.{ CodecAPI, HttpEndpoint, Metadata, PathParams }
import smithy4s.schema.Schema
import cats.implicits._
import play.api.libs.json.Json

import scala.concurrent.{ ExecutionContext, Future }

class SmithyPlayEndpoint[F[_] <: ContextRoute[_], Op[
  _,
  _,
  _,
  _,
  _
], I, E, O, SI, SO](
  impl: Interpreter[Op, F],
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  codecs: CodecAPI
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends AbstractController(cc) {

  private val httpEndpoint = HttpEndpoint.cast(endpoint)

  private val inputSchema: Schema[I]  = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataDecoder =
    Metadata.PartialDecoder.fromSchema(inputSchema)

  private val outputMetadataEncoder =
    Metadata.Encoder.fromSchema(outputSchema)

  def handler(v1: RequestHeader): Handler =
    httpEndpoint.map { httpEp =>
      Action.async(parse.raw) { implicit request =>
        val result: EitherT[Future, ContextRouteError, O] = for {
          pathParams <- getPathParams(v1, httpEp)
          metadata    = getMetadata(pathParams, v1)
          input      <- getInput(request, metadata)
          res        <- impl(endpoint.wrap(input))
                          .run(
                            RoutingContext
                              .fromRequest(request)
                          )
                          .map { case o: O =>
                            o
                          }
        } yield res
        result.value.map {
          case Left(value)  => handleFailure(value)
          case Right(value) => handleSuccess(value, httpEp.code)
        }
      }
    }
      .getOrElse(Action(NotFound("404")))

  def handleFailure(error: ContextRouteError): Result =
    Results.Status(error.statusCode)(
      Json.toJson(
        RoutingErrorResponse(error.message, error.additionalInfoErrorCode)
      )
    )

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
          request.contentType.get match {
            case "application/json" => parseJson(request, metadata)
            case _                  => parseRaw(request, metadata)
          }

      })
    )

  private def parseJson(request: Request[RawBuffer], metadata: Metadata) =
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
      codec            = codecs.compileCodec(inputSchema)
      c               <- codecs
                           .decodeFromByteBufferPartial(
                             codec,
                             request.body.asBytes().get.toByteBuffer
                           )
                           .leftMap(e => Smithy4PlayError(s"expected: ${e.expected}", 400))
    } yield metadataPartial.combine(c)

  private def parseRaw(request: Request[RawBuffer], metadata: Metadata) = {
    val nativeCodec: CodecAPI = CodecAPI.nativeStringsAndBlob(codecs)
    val input                 = ByteArray(request.body.asBytes().get.toArray)
    val codec                 = nativeCodec
      .compileCodec(inputSchema)
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
      bodyPartial     <- nativeCodec
                           .decodeFromByteArrayPartial(codec, input.array)
                           .leftMap(e => Smithy4PlayError(s"expected: ${e.expected}", 400))
    } yield metadataPartial.combine(bodyPartial)
  }

  private def getMetadata(pathParams: PathParams, request: RequestHeader) =
    Metadata(
      path = pathParams,
      headers = getHeaders(request),
      query = request.queryString.map { case (k, v) => (k.trim, v) }
    )

  private def handleSuccess(output: O, code: Int) = {
    val outputMetadata                  = outputMetadataEncoder.encode(output)
    val outputHeaders                   = outputMetadata.headers.map { case (k, v) =>
      (k.toString, v.mkString(""))
    }
    val contentType                     =
      outputHeaders.getOrElse("Content-Type", "application/json")
    val outputHeadersWithoutContentType = outputHeaders.-("Content-Type").toList
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
