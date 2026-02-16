package de.innfactory.smithy4play.routing.internal

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.{ logger, ContextRoute, RoutingResult }
import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.routing.context.RoutingContextBase
import de.innfactory.smithy4play.routing.middleware.Middleware
import de.innfactory.smithy4play.routing.internal.{
  toSmithy4sHttpRequest
}
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

  private val baseServerCodec: HttpUnaryServerCodecs.Builder[ContextRoute, RequestWrapped, Result] =
    HttpUnaryServerCodecs
      .builder[ContextRoute]
      .withErrorTypeHeaders(errorHeaders*)
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(Metadata.Encoder)
      .withBaseResponse(_ => Kleisli(ctx => EitherT.rightT[Future, Throwable](baseResponse)))
      .withWriteEmptyStructs(!_.isUnit)
      .withRequestTransformation[RequestWrapped](v => toSmithy4sHttpRequest(v))
      .withResponseTransformation[Result](v => Kleisli(ctx => EitherT.rightT[Future, Throwable](handleSuccess(v)(ctx))))

  private def handleSuccess(output: HttpResponse[Blob])(ctx: RoutingContextBase): Result = {
    val status                          = Results.Status(output.statusCode)
    val contentTypeKey                  = CaseInsensitive("content-type")
    val outputHeadersWithoutContentType = output.headers.-(contentTypeKey).toList.map(h => (h._1.toString, h._2.head))
    val contentType                     = output.headers.getOrElse(contentTypeKey, Seq())

    if (!output.body.isEmpty) {
      status(output.body.toArray).as(contentType.head).withHeaders(outputHeadersWithoutContentType*)
    } else {
      status("").withHeaders(outputHeadersWithoutContentType*)
    }
  }

  private val compileServerCodec = codec.buildServerCodecFromBase(baseServerCodec)

  private val router =
    PlayPartialFunctionRouter.partialFunction[Alg, ContextRoute, RequestHeader, RequestWrapped, Result](service)(
      impl,
      compileServerCodec,
      middleware.resolveMiddleware,
      addDecodedPathParams = (r, v, pathSegments) => r.copy(r.req, v, pathSegments)
    )

  val mapper: PartialFunction[RequestHeader, Request[RawBuffer] => RoutingResult[Result]] =
    new PartialFunction[RequestHeader, Request[RawBuffer] => RoutingResult[Result]] {
      override def isDefinedAt(v1: RequestHeader): Boolean = router.isDefinedAt(v1)

      override def apply(v1: RequestHeader): Request[RawBuffer] => RoutingResult[Result] = { request =>
        val hints                   = service.hints
        val ctx: RoutingContextBase = RoutingContextBase.fromRequest(request, hints, v1)

        if (logger.isDebugEnabled) {
          logger.debug(s"[Smithy4PlayRouterHandler] handle route for ${v1.method} ${v1.path}")
        }

        // Intentionally uses isDefinedAt + apply.
        // When Play (or our own router chaining) triggers isDefinedAt before apply,
        // PlayPartialFunctionRouter internally caches the match result to avoid
        // repeating the expensive endpoint search.
        router
          .apply(v1)
          .apply(RequestWrapped(request, Map.empty))
          .run(ctx)
      }
    }

  def routes(): PartialFunction[RequestHeader, Request[RawBuffer] => RoutingResult[Result]] = mapper
}
