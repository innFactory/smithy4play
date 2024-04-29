package de.innfactory.smithy4play

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play
import de.innfactory.smithy4play.middleware.MiddlewareBase
import play.api.mvc._
import smithy4s.codecs.PayloadError
import smithy4s.http._
import smithy4s.kinds.FunctorInterpreter
import smithy4s.schema.Schema
import smithy4s.{ Blob, Endpoint, Service }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SmithyPlayEndpoint[Alg[_[_, _, _, _, _]], F[_] <: ContextRoute[_], Op[_, _, _, _, _], I, E, O, SI, SO](
  service: Service[Alg],
  impl: FunctorInterpreter[Op, F],
  middleware: Seq[MiddlewareBase],
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  codecDecider: CodecDecider
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends AbstractController(cc) {

  private val httpEndpoint: Either[HttpEndpoint.HttpEndpointError, HttpEndpoint[I]] = HttpEndpoint.cast(endpoint.schema)
  private val serviceHints                                                          = service.hints
  private val endpointHints                                                         = endpoint.hints
  private val serviceContentType: String                                            = serviceHints.toMimeType

  private implicit val inputSchema: Schema[I]            = endpoint.input
  private implicit val outputSchema: Schema[O]           = endpoint.output
  private val outputMetadataEncoder: Metadata.Encoder[O] =
    Metadata.Encoder.fromSchema(outputSchema)
  def handler(v1: RequestHeader): Handler                =
    httpEndpoint.map { httpEp =>
      Action.async(parse.raw) { implicit request =>
        if (request.body.size > 0 && request.body.asBytes().isEmpty) {
          logger.error(
            "received body size does not equal the parsed body size. \n" +
              "This is probably due to the body being too large and thus play is unable to parse.\n" +
              "Try setting play.http.parser.maxMemoryBuffer in application.conf"
          )
        }

        implicit val epContentType: ContentType = ContentType(request.contentType.getOrElse(serviceContentType))
        val result                              = for {
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

  private def mapToEndpointResult(
    statusCode: Int
  )(output: O): HttpResponse[Blob] = {
    val outputMetadata = outputMetadataEncoder.encode(output).headers.get(CaseInsensitive("content-type")) match {
      case Some(value) => value
      case None        => Seq(serviceContentType)
    }
    codecDecider
      .httpMessageEncoder(outputMetadata)
      .fromSchema(outputSchema)
      .write(
        HttpResponse(
          statusCode = statusCode,
          headers = Map.empty,
          body = Blob.empty
        ),
        output
      )
  }

  private def getPathParams(
    v1: RequestHeader,
    httpEp: HttpEndpoint[I]
  )(implicit defaultContentType: ContentType): EitherT[Future, ContextRouteError, Map[String, String]] =
    EitherT(
      Future(
        matchRequestPath(v1, httpEp)
          .toRight[ContextRouteError](
            Smithy4PlayError(
              "Error in extracting PathParams",
              smithy4play.Status(Map.empty, 400),
              contentType = defaultContentType.value
            )
          )
      )
    )

  private def getInput(
    request: Request[RawBuffer],
    metadata: Metadata
  )(implicit defaultContentType: ContentType): EitherT[Future, ContextRouteError, I] =
    EitherT {
      Future {
        val codec = codecDecider.requestDecoder(Seq(defaultContentType.value))
        codec
          .fromSchema(inputSchema)
          .decode({
            val body = request.body.asBytes().map(b => Blob(b.toByteBuffer)).getOrElse(Blob.empty)
            PlayHttpRequest(
              metadata = metadata,
              body = body
            )
          })
      }
    }.leftMap {
      case error: PayloadError  =>
        Smithy4PlayError(
          error.filterMessage,
          smithy4play.Status(Map.empty, 400),
          contentType = defaultContentType.value
        )
      case error: MetadataError =>
        Smithy4PlayError(
          error.filterMessage,
          smithy4play.Status(Map.empty, 400),
          contentType = defaultContentType.value
        )
    }

  private def getMetadata(pathParams: PathParams, request: RequestHeader): Metadata =
    Metadata(
      path = pathParams,
      headers = getHeaders(request.headers),
      query = request.queryString.map { case (k, v) => (k.trim, v) }
    )

  private def handleFailure(error: ContextRouteError): Result =
    Results
      .Status(error.status.statusCode)(error.parse)
      .withHeaders(error.status.headers.toList: _*)
      .as(error.contentType)

  private def handleSuccess(output: HttpResponse[Blob]): Result = {
    val status                          = Results.Status(output.statusCode)
    val contentTypeKey                  = CaseInsensitive("content-type")
    val outputHeadersWithoutContentType =
      output.headers.-(contentTypeKey).toList.map(h => (h._1.toString, h._2.head))
    val contentType                     =
      output.headers.getOrElse(contentTypeKey, Seq(serviceContentType))

    if (!output.body.isEmpty) {
      status(output.body.toArray)
        .as(contentType.head)
        .withHeaders(outputHeadersWithoutContentType: _*)
    } else {
      status("").withHeaders(outputHeadersWithoutContentType: _*)
    }
  }

}
