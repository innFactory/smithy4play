package de.innfactory.smithy4play

import akka.util.ByteString
import cats.data.{ EitherT, Kleisli }
import cats.implicits.toBifunctorOps
import de.innfactory.smithy4play
import de.innfactory.smithy4play.middleware.MiddlewareBase
import play.api.mvc._
import smithy4s.codecs.{
  BlobDecoder,
  BlobEncoder,
  PayloadDecoder,
  PayloadEncoder,
  PayloadError,
  StringAndBlobCodecs,
  Writer
}
import smithy4s.http.{ HttpEndpoint, HttpRequest, HttpResponse, HttpRestSchema, Metadata, PathParams }
import smithy4s.json.{ Json, JsonPayloadCodecCompiler }
import smithy4s.json.Json.payloadCodecs
import smithy4s.kinds.FunctorInterpreter
import smithy4s.schema.CachedSchemaCompiler.getOrElse
import smithy4s.schema.{ CachedSchemaCompiler, Schema }
import smithy4s.xml.Xml.encoders
import smithy4s.xml.{ Xml, XmlDecodeError }
import smithy4s.{ Blob, Endpoint, Service }

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
  endpoint: Endpoint[Op, I, E, O, SI, SO]
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends AbstractController(cc) {

  private val httpEndpoint: Either[HttpEndpoint.HttpEndpointError, HttpEndpoint[I]] = HttpEndpoint.cast(endpoint.schema)
  private val serviceHints                                                          = service.hints
  private val endpointHints                                                         = endpoint.hints

  private implicit val inputSchema: Schema[I]  = endpoint.input
  private implicit val outputSchema: Schema[O] = endpoint.output

  // private val inputMetadataDecoder: Metadata.Decoder[I] =
  //  Metadata.Decoder.fromSchema(inputSchema)

  private val outputMetadataEncoder: Metadata.Encoder[O] =
    Metadata.Encoder.fromSchema(outputSchema)

  private implicit val jsonEncoders: CachedSchemaCompiler[PayloadEncoder] = payloadCodecs.encoders
  private implicit val jsonDecoders: CachedSchemaCompiler[PayloadDecoder] = payloadCodecs.decoders

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
          pathParams        <- getPathParams(v1, httpEp)
          metadata           = getMetadata(pathParams, v1)
          input             <- getInput(request, metadata)
          endpointLogic      = impl(endpoint.wrap(input))
                                 .asInstanceOf[Kleisli[RouteResult, RoutingContext, O]]
                                 .map(mapToEndpointResult(httpEp.code))
          chainedMiddlewares = middleware.foldRight(endpointLogic)((a, b) => a.middleware(b.run))
          res               <-
            chainedMiddlewares.run(RoutingContext.fromRequest(request, serviceHints, endpointHints, v1))
        } yield res
        result.value.map {
          case Left(value)  => handleFailure(value)
          case Right(value) => handleSuccess(value)
        }
      }
    }
      .getOrElse(Action(NotFound("404")))

  private def mapToEndpointResult(statusCode: Int)(o: O): EndpointResult = {
    val outputMetadata = outputMetadataEncoder.encode(o)
    val outputHeaders  = outputMetadata.headers.map { case (k, v) =>
      (k.toString.toLowerCase, v.mkString(""))
    }
    val contentType    = outputHeaders.getOrElse("content-type", "application/json")
    val codec          = CodecDecider.encoder(Seq(contentType))
    logger.debug(s"[SmithyPlayEndpoint] Headers: ${outputHeaders.mkString("|")}")
    EndpointResult(codec.fromSchema(outputSchema).encode(o), status = smithy4play.Status(outputHeaders, statusCode))
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
              smithy4play.Status(Map.empty, 400)
            )
          )
      )
    )

  private def getInput(
    request: Request[RawBuffer],
    metadata: Metadata
  ): EitherT[Future, ContextRouteError, I] =
    EitherT(
      Future {
        val contentType = request.contentType.getOrElse("application/json")
        (contentType match {
          case "application/json" => payloadCodecs.decoders
          case "application/xml"  => Xml.decoders
          case _                  =>
            CachedSchemaCompiler
              .getOrElse(smithy4s.codecs.StringAndBlobCodecs.decoders, jsonDecoders)
        })
          .fromSchema(inputSchema)
          .decode(request.body.asBytes().map(b => Blob(b.toByteBuffer)).getOrElse(Blob.empty))
      }
    ).leftMap { error: PayloadError =>
      Smithy4PlayError(error.expected, smithy4play.Status(Map.empty, 500))
    }

  private def getMetadata(pathParams: PathParams, request: RequestHeader): Metadata =
    Metadata(
      path = pathParams,
      headers = getHeaders(request.headers),
      query = request.queryString.map { case (k, v) => (k.trim, v) }
    )

  private def handleFailure(error: ContextRouteError): Result =
    Results.Status(error.status.statusCode)(error.toJson).withHeaders(error.status.headers.toList: _*)

  private def handleSuccess(output: EndpointResult): Result = {
    val status                          = Results.Status(output.status.statusCode)
    val outputHeadersWithoutContentType = output.status.headers.-("content-type").toList
    val contentType                     =
      output.status.headers.getOrElse("content-type", "application/json")

    if (!output.body.isEmpty) {
      status(output.body.toArray)
        .as(contentType)
        .withHeaders(outputHeadersWithoutContentType: _*)
    } else {
      status("").withHeaders(outputHeadersWithoutContentType: _*)
    }
  }

}
