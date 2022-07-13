package de.innfactory.play4s

import cats.data.EitherT
import play.api.mvc.{AbstractController, ControllerComponents, Handler, RawBuffer, Request, RequestHeader, Results}
import smithy4s.{ByteArray, Endpoint, Interpreter}
import smithy4s.http.{CodecAPI, HttpEndpoint, Metadata, PathParams}
import smithy4s.schema.Schema
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}

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

  private val inputSchema: Schema[I] = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataDecoder =
    Metadata.PartialDecoder.fromSchema(inputSchema)

  private val outputMetadataEncoder =
    Metadata.Encoder.fromSchema(outputSchema)

  def handler(v1: RequestHeader): Handler = {
    HttpEndpoint
      .cast(endpoint)
      .map(httpEp => {
        Action.async(parse.raw) { implicit request =>
          val result: EitherT[Future, ContextRouteError, O] = for {
            pathParams <- getPathParams(v1, httpEp)
            metadata = getMetadata(pathParams, v1)
            input <- getInput(request, metadata)
            res <- impl(endpoint.wrap(input))
              .run(
                RoutingContext
                  .fromRequest(request)
              )
              .map { case o: O =>
                o
              }
          } yield res
          result.value.map {
            case Left(value)  => Results.Status(value.statusCode)(value.message)
            case Right(value) => handleSuccess(value, httpEp.code)
          }
        }
      })
      .getOrElse(Action { NotFound("404") })
  }

  private def getPathParams(v1: RequestHeader, httpEp: HttpEndpoint[I]) = {
    EitherT(
      Future(
        httpEp
          .matches(v1.path.replaceFirst("/", "").split("/"))
          .toRight[ContextRouteError](de.innfactory.play4s.BadRequest("Error in extracting PathParams"))
      )
    )
  }

  private def getInput(
      request: Request[RawBuffer],
      metadata: Metadata
  ): EitherT[Future, ContextRouteError, I] = {
    EitherT(
      Future(inputMetadataDecoder.total match {
        case Some(value) =>
          value.decode(metadata).leftMap(e => de.innfactory.play4s.BadRequest(e.getMessage()))
        case None =>
          request.contentType.get match {
            case "application/json" => parseJson(request, metadata)
            case _                  => parseRaw(request, metadata)
          }

      })
    )
  }

  private def parseJson(request: Request[RawBuffer], metadata: Metadata) = {
    for {
      metadataPartial <- inputMetadataDecoder
        .decode(metadata)
        .leftMap(e => {
          de.innfactory.play4s.BadRequest(e.getMessage())
        })
      codec = codecs.compileCodec(inputSchema)
      c <- codecs
        .decodeFromByteBufferPartial(
          codec,
          request.body.asBytes().get.toByteBuffer
        )
        .leftMap(e => {
          de.innfactory.play4s.BadRequest(e.message)
        })
    } yield metadataPartial.combine(c)
  }

  private def parseRaw(request: Request[RawBuffer], metadata: Metadata) = {
    val nativeCodec: CodecAPI = CodecAPI.nativeStringsAndBlob(codecs)
    val input = ByteArray(request.body.asBytes().get.toArray)
    val codec = nativeCodec
      .compileCodec(inputSchema)
    for {
      metadataPartial <- inputMetadataDecoder
        .decode(metadata)
        .leftMap(e => {
          de.innfactory.play4s.BadRequest(e.getMessage())
        })
      bodyPartial <- nativeCodec
        .decodeFromByteArrayPartial(codec, input.array)
        .leftMap(e => de.innfactory.play4s.BadRequest(e.getMessage()))
    } yield metadataPartial.combine(bodyPartial)
  }

  private def getMetadata(pathParams: PathParams, request: RequestHeader) =
    Metadata(
      path = pathParams,
      headers = getHeaders(request),
      query = request.queryString
        .map { case (k, v) => (k.trim, v) }
    )

  private def handleSuccess(output: O, code: Int) = {
    val outputMetadata = outputMetadataEncoder.encode(output)
    val outputHeaders = outputMetadata.headers.map { case (k, v) =>
      (k.toString, v.mkString(""))
    }.toList
    val status = Results.Status(code)
    val codecA = codecs.compileCodec(outputSchema)
    val expectBody = Metadata.PartialDecoder
      .fromSchema(outputSchema)
      .total
      .isEmpty // expect body if metadata decoder is not total
    if (expectBody) {
      status(codecs.writeToArray(codecA, output)).withHeaders(outputHeaders: _*)
    } else status("")

  }

}
