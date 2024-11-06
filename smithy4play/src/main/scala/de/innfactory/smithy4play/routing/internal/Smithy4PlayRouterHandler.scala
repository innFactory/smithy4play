package de.innfactory.smithy4play.routing.internal

import cats.data.{EitherT, Kleisli}
import de.innfactory.smithy4play.{ContextRoute, RoutingResult, logger}
import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.routing.context.RoutingContextBase
import de.innfactory.smithy4play.routing.middleware.Middleware
import de.innfactory.smithy4play.routing.internal.{deconstructPath, getSmithy4sHttpMethod, toSmithy4sHttpRequest, toSmithy4sHttpUri}
import de.innfactory.smithy4play.telemetry.Telemetry
import play.api.mvc.*
import play.api.routing.Router.Routes
import smithy4s.*

class Smithy4PlayRouterHandler(router: PartialFunction[RequestHeader, RequestWrapped => ContextRoute[Result]]) {
  
  def applyHandler(v1: RequestHeader, serviceHints: Hints): Request[RawBuffer] => RoutingResult[Result] = { request =>
    val ctx: RoutingContextBase = RoutingContextBase.fromRequest(request,serviceHints, v1)
    handleForInstrument(v1.path, v1.method)
    router.apply(v1)(RequestWrapped(request, Map.empty)).run(ctx)
  }
  
  def handleForInstrument(path: String, method: String): String = {
    logger.debug(s"[${this.getClass.getName}] handle route for ${method} ${path}")
    ""
  }
  
  def isDefinedAtHandler(v1: RequestHeader): Boolean = {
    val isdefined = router.isDefinedAt(v1)
    if (!isdefined) logger.debug(s"[${this.getClass.getName}] router is not defined at ${isdefined} ${v1}")
    isdefined
  }
}
