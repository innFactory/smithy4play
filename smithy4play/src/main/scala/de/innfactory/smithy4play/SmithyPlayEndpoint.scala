package de.innfactory.smithy4play

import akka.util.ByteString
import cats.data.{ EitherT, Kleisli }
import cats.implicits.toBifunctorOps
import de.innfactory.smithy4play.middleware.MiddlewareBase
import play.api.mvc._
import smithy4s.http.{ CodecAPI, HttpEndpoint, Metadata, PathParams }
import smithy4s.kinds.FunctorInterpreter
import smithy4s.schema.Schema
import smithy4s.{ ByteArray, Endpoint, Service }

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

        val result = for {
          pathParams   <- getPathParams(v1, httpEp)
          metadata      = getMetadata(pathParams, v1)
          input        <- getInput(request, metadata)
          endpointLogic = impl(endpoint.wrap(input))
                            .asInstanceOf[Kleisli[RouteResult, RoutingContext, O]]
                            .map(mapToEndpointResult)

          chainedMiddlewares = middleware.foldRight(endpointLogic)((a, b) => a.middleware(b.run))
          res               <-
            chainedMiddlewares.run(RoutingContext.fromRequest(request, serviceHints, endpointHints, v1))
        } yield res
        result.value.map {
          case Left(value)  => handleFailure(value)
          case Right(value) => handleSuccess(value, httpEp.code)
        }
      }
    }
      .getOrElse(Action(NotFound("404")))

  private def mapToEndpointResult(o: O): EndpointResult = {
    val outputMetadata = outputMetadataEncoder.encode(o)
    val outputHeaders  = outputMetadata.headers.map { case (k, v) =>
      (k.toString.toLowerCase, v.mkString(""))
    }
    val contentType    =
      outputHeaders.getOrElse("content-type", "application/json")
    val codecApi       = contentType match {
      case "application/json" => codecs
      case _                  => CodecAPI.nativeStringsAndBlob(codecs)
    }
    logger.debug(s"[SmithyPlayEndpoint] Headers: ${outputHeaders.mkString("|")}")

    val codec      = codecApi.compileCodec(outputSchema)
    val expectBody = Metadata.PartialDecoder
      .fromSchema(outputSchema)
      .total
      .isEmpty // expect body if metadata decoder is not total
    val body = if (expectBody) Some(codecApi.writeToArray(codec, o)) else None
    EndpointResult(body, outputHeaders)
  }

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

  private def handleFailure(error: ContextRouteError): Result =
    Results.Status(error.statusCode)(error.toJson)

  private def handleSuccess(output: EndpointResult, code: Int): Result = {
    val status                          = Results.Status(code)
    val outputHeadersWithoutContentType = output.headers.-("content-type").toList
    val contentType                     =
      output.headers.getOrElse("content-type", "application/json")

    output.body match {
      case Some(value) =>
        status(value)
          .as(contentType)
          .withHeaders(outputHeadersWithoutContentType: _*)
      case None        => status("").withHeaders(outputHeadersWithoutContentType: _*)
    }
  }

}
