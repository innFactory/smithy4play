package de.innfactory.smithy4play

import cats.data.{EitherT, Kleisli}
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId, toTraverseOps}
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.typesafe.config.Config
import play.api.http.MimeTypes
import play.api.mvc
import play.api.mvc.{AbstractController, ControllerComponents, Handler, RawBuffer, Request, RequestHeader, Result, Results, WrappedRequest}
import play.api.routing.Router.Routes
import smithy4s.{Blob, Endpoint, Hints, Service, ShapeId}
import smithy4s.capability.MonadThrowLike
import smithy4s.codecs.{BlobDecoder, BlobEncoder, PayloadDecoder, PayloadEncoder, StringAndBlobCodecs}
import smithy4s.http.{HttpEndpoint, HttpResponse, HttpUnaryServerCodecs, HttpUnaryServerRouter, PathSegment}
import smithy4s.json.{Json, JsonPayloadCodecCompiler}
import smithy4s.kinds.{BiFunctorAlgebra, FunctorAlgebra, Kind1, PolyFunction5}
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.xml.Xml
import smithy4s.http.*
import smithy4s.interopcats.monadThrowShim
import smithy4s.server.UnaryServerCodecs

import scala.concurrent.{ExecutionContext, Future}

class SmithyPlayRouter[Alg[_[_, _, _, _, _]]](
  impl: FunctorAlgebra[Alg, FutureMonadError],
  service: smithy4s.Service[Alg]
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends AbstractController(cc) {

  type PlayTransformation = Request[RawBuffer] => FutureMonadError[Result]

  object InjectorMiddleware extends Endpoint.Middleware.Standard[PlayTransformation] {
    def prepare(
      serviceId: ShapeId,
      endpointId: ShapeId,
      serviceHints: Hints,
      endpointHints: Hints
    ): PlayTransformation => PlayTransformation =
      underlyingClient => { v =>
        val k: Kleisli[MonadErrorResult, RoutingContext, Result] = Kleisli { ctx =>
          val routingContextWithEndpointHints = RoutingContextWithEndpointHints(
            ctx.headers,
            ctx.serviceHints,
            endpointHints,
            ctx.attributes,
            ctx.requestHeader,
            ctx.rawBody
          )
          val x: MonadErrorResult[Result]     = underlyingClient(v).run(routingContextWithEndpointHints)
          x
        }

        k

      }
  }

  val baseResponse = HttpResponse(200, Map.empty, Blob.empty)

  private val hintMask =
    alloy.SimpleRestJson.protocol.hintMask


  private val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(
      Json.jsoniter.withHintMask(hintMask)
    )

  // val mediaType = HttpMediaType("application/json")
  private val payloadEncoders: BlobEncoder.Compiler =
    jsonCodecs.encoders

  private val xmlPayloadEncoder: BlobEncoder.Compiler =
      Xml.encoders


  private val errorHeaders = List(
    smithy4s.http.errorTypeHeader
  )

  val baseServerCodec: HttpUnaryServerCodecs.Builder[FutureMonadError, Request[RawBuffer], Result] = HttpUnaryServerCodecs
      .builder[FutureMonadError]
      .withErrorTypeHeaders(errorHeaders: _*)
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(Metadata.Encoder)
      .withBaseResponse(_ => Kleisli(ctx => EitherT.rightT[Future, Throwable](baseResponse)))
      .withWriteEmptyStructs(!_.isUnit)
      .withRequestTransformation[Request[RawBuffer]](v => toSmithy4sHttpRequest(v))
      .withResponseTransformation[Result](v => Kleisli(ctx => EitherT.rightT[Future, Throwable](handleSuccess(v)(ctx))))

  private def handleSuccess(output: HttpResponse[Blob])(ctx: RoutingContext): Result = {

    ctx match
      case RoutingContextWithEndpointHints(headers, serviceHints, endpointHints, attributes, requestHeader, rawBody) => logger.error(endpointHints.toString)
      case RoutingContextWithoutEndpointHints(headers, serviceHints, attributes, requestHeader, rawBody) => logger.error("Wrong Context")

    val status                          = Results.Status(output.statusCode)
    val contentTypeKey                  = CaseInsensitive("content-type")
    val outputHeadersWithoutContentType = output.headers.-(contentTypeKey).toList.map(h => (h._1.toString, h._2.head))
    val contentType                     = output.headers.getOrElse(contentTypeKey, Seq())

    if (!output.body.isEmpty) {
      status(output.body.toArray).as(contentType.head).withHeaders(outputHeadersWithoutContentType: _*)
    } else {
      status("").withHeaders(outputHeadersWithoutContentType: _*)
    }
  }

  private val stringAndBlobEncoder = CachedSchemaCompiler.getOrElse(smithy4s.codecs.StringAndBlobCodecs.encoders, jsonCodecs.encoders)
  private val stringAndBlobDecoder = CachedSchemaCompiler.getOrElse(smithy4s.codecs.StringAndBlobCodecs.decoders, jsonCodecs.decoders)

  def decoder(
               contentType: String
             ): CachedSchemaCompiler[BlobDecoder] =
    contentType match {
      case MimeTypes.JSON => jsonCodecs.decoders
      case MimeTypes.XML => Xml.decoders
      case _ => stringAndBlobDecoder
    }

  case class Encoders(errorEncoder: CachedSchemaCompiler[BlobEncoder], payloadEncoder: CachedSchemaCompiler[BlobEncoder])

  def encoder(
               contentType: String
             ): CachedSchemaCompiler[BlobEncoder] = {
    contentType match {
      case MimeTypes.JSON => jsonCodecs.encoders
      case MimeTypes.XML => Xml.encoders
      case _ => stringAndBlobEncoder
    }
  }


  private def resolveCodec(contentType: ContentType) = {
    baseServerCodec
      .withResponseMediaType(contentType.output)
      .withSuccessBodyEncoders(encoder(contentType.output))
      .withBodyDecoders(decoder(contentType.input))
      .withErrorBodyEncoders(encoder(contentType.error))
      .build()
  }

  private val router = PlayPartialFunctionRouter.partialFunction[Alg, FutureMonadError, RequestHeader, Request[RawBuffer], Result](service)(
      impl,
      resolveCodec,
      InjectorMiddleware,
      getMethod = requestHeader => getSmithy4sHttpMethod(requestHeader.method),
      getUri = requestHeader => {
        val pathParams = deconstructPath(requestHeader.path)
        toSmithy4sHttpUri(pathParams, requestHeader.secure, requestHeader.host, requestHeader.queryString)
      },
      addDecodedPathParams = (r, v) => r
    )

  val handler: Routes = new PartialFunction[RequestHeader, Handler] {

    override def isDefinedAt(x: RequestHeader): Boolean = router.isDefinedAt(x)

    override def apply(v1: RequestHeader): Handler = Action.async(parse.raw) { implicit request =>
      val ctx: RoutingContext = RoutingContext.fromRequest(request, service.hints, v1)
      router(v1)(request)(ctx).value.map {
        case Left(value)  => Results.Status(500)
        case Right(value) => value
      }
    }
  }

  def routes(): Routes = handler

//
//  def routes(middlewares: Seq[MiddlewareBase], readerConfig: ReaderConfig): Routes = {
//
//    val interpreter: PolyFunction5[service.Operation, Kind1[F]#toKind5]             = service.toPolyFunction[Kind1[F]#toKind5](impl)
//    val endpoints: Seq[service.Endpoint[_, _, _, _, _]]                             = service.endpoints
//    val httpEndpoints: Seq[Either[HttpEndpoint.HttpEndpointError, HttpEndpoint[_]]] =
//      endpoints.map(ep => HttpEndpoint.cast(ep.schema))
//    val codecDecider                                                                = CodecDecider(readerConfig)
//
//    new PartialFunction[RequestHeader, Handler] {
//      override def isDefinedAt(x: RequestHeader): Boolean = {
//        logger.debug("[SmithyPlayRouter] calling isDefinedAt on service: " + service.id.name + " for path: " + x.path)
//        httpEndpoints.exists(ep => ep.exists(checkIfRequestHeaderMatchesEndpoint(x, _)))
//      }
//
//      override def apply(v1: RequestHeader): Handler = {
//        logger.debug("[SmithyPlayRouter] calling apply on: " + service.id.name)
//        for {
//          zippedEndpoints         <- endpoints.map(ep => HttpEndpoint.cast(ep.schema).map((ep, _))).sequence
//          endpointAndHttpEndpoint <-
//            zippedEndpoints
//              .find(ep => checkIfRequestHeaderMatchesEndpoint(v1, ep._2))
//              .toRight(
//                HttpEndpoint.HttpEndpointError("Could not cast Endpoint to HttpEndpoint, likely a bug in smithy4s")
//              )
//        } yield new SmithyPlayEndpoint(
//          service,
//          interpreter,
//          middlewares,
//          endpointAndHttpEndpoint._1,
//          codecDecider
//        ).handler(v1)
//      } match {
//        case Right(value) => value
//        case Left(value)  => throw new Exception(value.message)
//      }
//
//    }
//  }
//
//  private def checkIfRequestHeaderMatchesEndpoint(
//    x: RequestHeader,
//    ep: HttpEndpoint[_]
//  ): Boolean = {
//    ep.path.map {
//      case PathSegment.StaticSegment(value) => value
//      case PathSegment.LabelSegment(value)  => value
//      case PathSegment.GreedySegment(value) => value
//    }
//      .filter(_.contains(" "))
//      .foreach(value => logger.info("following pathSegment contains a space: " + value))
//    matchRequestPath(x, ep).isDefined && x.method == ep.method.showUppercase
//  }
}
