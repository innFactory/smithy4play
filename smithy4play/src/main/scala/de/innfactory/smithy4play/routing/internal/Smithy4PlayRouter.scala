package de.innfactory.smithy4play.routing.internal

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.{ ContextRoute, RoutingResult }
import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.routing.context.RoutingContextBase
import de.innfactory.smithy4play.routing.middleware.Middleware
import de.innfactory.smithy4play.routing.internal.{
  deconstructPath,
  getSmithy4sHttpMethod,
  toSmithy4sHttpRequest,
  toSmithy4sHttpUri
}
import de.innfactory.smithy4play.telemetry.Telemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import play.api.mvc.*
import play.api.routing.Router.Routes
import smithy4s.*
import smithy4s.http.*
import smithy4s.interopcats.monadThrowShim
import smithy4s.kinds.FunctorAlgebra

import scala.concurrent.{ ExecutionContext, Future }

class Smithy4PlayRouter[Alg[_[_, _, _, _, _]]](
  impl: FunctorAlgebra[Alg, ContextRoute],
  service: smithy4s.Service[Alg],
  codec: Codec,
  middleware: Middleware
)(implicit ec: ExecutionContext) {

  private val baseResponse = HttpResponse(200, Map.empty, Blob.empty)
  private val errorHeaders = List(smithy4s.http.errorTypeHeader)

  private val baseServerCodec: HttpUnaryServerCodecs.Builder[ContextRoute, Request[RawBuffer], Result] =
    HttpUnaryServerCodecs
      .builder[ContextRoute]
      .withErrorTypeHeaders(errorHeaders: _*)
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(Metadata.Encoder)
      .withBaseResponse(_ => Kleisli(ctx => EitherT.rightT[Future, Throwable](baseResponse)))
      .withWriteEmptyStructs(!_.isUnit)
      .withRequestTransformation[Request[RawBuffer]](v => toSmithy4sHttpRequest(v))
      .withResponseTransformation[Result](v => Kleisli(ctx => EitherT.rightT[Future, Throwable](handleSuccess(v)(ctx))))

  private def handleSuccess(output: HttpResponse[Blob])(ctx: RoutingContextBase): Result = {
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
   
  private val compileServerCodec = codec.buildServerCodecFromBase(baseServerCodec)

  private val router =
    PlayPartialFunctionRouter.partialFunction[Alg, ContextRoute, RequestHeader, Request[RawBuffer], Result](service)(
      impl,
      compileServerCodec,
      middleware.resolveMiddleware,
      getMethod = requestHeader => getSmithy4sHttpMethod(requestHeader.method),
      getUri = requestHeader =>
        toSmithy4sHttpUri(
          deconstructPath(requestHeader.path),
          requestHeader.secure,
          requestHeader.host,
          requestHeader.queryString
        ),
      addDecodedPathParams = (r, v) => r
    )

  private val handler = new PartialFunction[RequestHeader, Request[RawBuffer] => RoutingResult[Result]] {

    override def isDefinedAt(x: RequestHeader): Boolean = router.isDefinedAt(x)

    override def apply(v1: RequestHeader): Request[RawBuffer] => RoutingResult[Result] = { request =>
      val ctx: RoutingContextBase = RoutingContextBase.fromRequest(request, service.hints, v1)
      router.apply(v1)(request).run(ctx)
    }
  }

  def routes(): PartialFunction[RequestHeader, Request[RawBuffer]=> RoutingResult[Result]] = handler
}
