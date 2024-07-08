package de.innfactory.smithy4play.routing.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.{ logger, ContextRoute, RoutingResult }
import de.innfactory.smithy4play.routing.*
import de.innfactory.smithy4play.routing.context.{ RoutingContext, RoutingContextBase }
import play.api.mvc.{ RawBuffer, Request, RequestHeader, Result }
import smithy4s.{ Endpoint, Hints, ShapeId }

private[routing] class InjectorMiddleware(
  smithy4PlayMiddleware: (RoutingContext, RoutingContext => RoutingResult[Result]) => RoutingResult[Result]
) extends Endpoint.Middleware.Standard[PlayTransformation] {

  def middleware(
    f: RoutingContextBase => RoutingResult[Result],
    transform: RoutingContextBase => RoutingContext
  ): ContextRoute[Result] =
    Kleisli { r =>
      smithy4PlayMiddleware(transform(r), f)
    }

  def prepare(
    serviceId: ShapeId,
    endpointId: ShapeId,
    serviceHints: Hints,
    endpointHints: Hints
  ): PlayTransformation => PlayTransformation = {

    def routingContextWithEndpointHints(ctx: RoutingContextBase) = RoutingContext(
      ctx.headers,
      ctx.serviceHints,
      endpointHints,
      ctx.attributes,
      ctx.requestHeader,
      ctx.rawBody
    )

    underlyingClient => { v =>
      val x: ContextRoute[Result] = underlyingClient(v)
      middleware(
        x.run,
        routingContextWithEndpointHints
      )
    }
  }
}
