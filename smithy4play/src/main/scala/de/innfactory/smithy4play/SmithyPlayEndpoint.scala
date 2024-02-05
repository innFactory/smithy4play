package de.innfactory.smithy4play

import cats.MonadThrow
import cats.data.{ EitherT, Kleisli }
import cats.implicits.{ toFunctorOps, toTraverseOps }
import de.innfactory.smithy4play
import de.innfactory.smithy4play.middleware.MiddlewareBase
import fs2.Compiler.Target.{ forConcurrent, forSync }
import play.api.mvc._
import smithy4s.capability.MonadThrowLike
import smithy4s.codecs.{ Decoder, PayloadError }
import smithy4s.http._
import smithy4s.kinds.FunctorInterpreter
import smithy4s.schema.Schema
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

  private val outputMetadataEncoder: Metadata.Encoder[O] =
    Metadata.Encoder.fromSchema(HttpRestSchema.OnlyMetadata(outputSchema).schema)

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

  private def mapToEndpointResult(statusCode: Int)(o: O): EndpointRequest = {
    val outputMetadata = outputMetadataEncoder.encode(o)
    val outputHeaders  = outputMetadata.headers.map { case (k, v) =>
      (k.toString.toLowerCase, v.mkString(""))
    }
    val contentType    = outputHeaders.getOrElse("content-type", "application/json")
    CodecDecider
      .requestEncoder(Seq(contentType))
      .fromSchema(outputSchema)
      .write(PlayHttpRequest(Blob.empty, Metadata.empty.copy(statusCode = Some(statusCode))), o)
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
    EitherT {
      Future {
        val contentType = metadata.headers.getOrElse(CaseInsensitive("content-type"), Seq("application/json"))
        val codec       = CodecDecider.requestDecoder(Seq(contentType.head))
        codec
          .fromSchema(inputSchema)
          .decode({
            val body = request.body.asBytes().map(b => Blob(b.toByteBuffer)).getOrElse(Blob.empty)
            PlayHttpRequest(
              metadata = metadata,
              // metadata = metadata.copy(headers = Map(CaseInsensitive("Host") -> List("localhost"), CaseInsensitive("cOnTeNt-TyPe") -> List("image/png"))),
              body = body
            )
          })
      }
    }.leftMap {
      case error: PayloadError  =>
        println(error)
        Smithy4PlayError("expected: " + error.expected, smithy4play.Status(Map.empty, 500))
      case error: MetadataError =>
        println(error)
        Smithy4PlayError(error.getMessage(), smithy4play.Status(Map.empty, 500))
    }

  private def getMetadata(pathParams: PathParams, request: RequestHeader): Metadata =
    Metadata(
      path = pathParams,
      headers = getHeaders(request.headers),
      query = request.queryString.map { case (k, v) => (k.trim, v) }
    )

  private def handleFailure(error: ContextRouteError): Result =
    Results.Status(error.status.statusCode)(error.toJson).withHeaders(error.status.headers.toList: _*)

  private def handleSuccess(output: EndpointRequest): Result = {
    val status                          = Results.Status(output.metadata.statusCode.getOrElse(200))
    val outputHeadersWithoutContentType =
      output.metadata.headers.-(CaseInsensitive("content-type")).toList.map(h => (h._1.toString, h._2.head))
    val contentType                     =
      output.metadata.headers.getOrElse(CaseInsensitive("content-type"), Seq("application/json"))

    if (!output.body.isEmpty) {
      status(output.body.toArray)
        .as(contentType.head)
        .withHeaders(outputHeadersWithoutContentType: _*)
    } else {
      status("").withHeaders(outputHeadersWithoutContentType: _*)
    }
  }

}
