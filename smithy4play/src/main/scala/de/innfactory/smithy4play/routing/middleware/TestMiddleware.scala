package de.innfactory.smithy4play.routing.middleware

import cats.data.Kleisli
import de.innfactory.smithy4play.RoutingResult
import de.innfactory.smithy4play.routing.*
import de.innfactory.smithy4play.routing.context.{RoutingContext, RoutingContextBase}
import play.api.mvc.{RawBuffer, Request, RequestHeader, Result}
import smithy4s.{Endpoint, Hints, ShapeId}

private[routing] object TestMiddleware extends Endpoint.Middleware.Standard[PlayTransformation] {
  def prepare(
    serviceId: ShapeId,
    endpointId: ShapeId,
    serviceHints: Hints,
    endpointHints: Hints
  ): PlayTransformation => PlayTransformation =
    underlyingClient => { v =>
      val k: Kleisli[RoutingResult, RoutingContextBase, Result] = Kleisli { ctx =>
        val routingContextWithEndpointHints = RoutingContext(
          ctx.headers,
          ctx.serviceHints,
          endpointHints,
          ctx.attributes,
          ctx.requestHeader,
          ctx.rawBody
        )
        val x: RoutingResult[Result]     = underlyingClient(v).run(routingContextWithEndpointHints)
        x
      }
      k
    }
}
