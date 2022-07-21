package de.innfactory.play4s

import play.api.mvc.{ControllerComponents, Handler, RequestHeader}
import play.api.routing.Router.Routes
import smithy4s.http.{HttpEndpoint, HttpMethod, PathSegment, matchPath}
import smithy4s.{GenLift, HintMask, Monadic}
import smithy4s.internals.InputOutput

import scala.concurrent.ExecutionContext

class SmithyPlayRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
    _
] <: ContextRoute[_]](
    impl: Monadic[Alg, F]
)(implicit cc: ControllerComponents, ec: ExecutionContext) {

  def routes()(implicit
      serviceProvider: smithy4s.Service.Provider[Alg, Op]
  ): Routes = {

    val service = serviceProvider.service
    val interpreter = service.asTransformation[GenLift[F]#λ](impl)
    val endpoints = service.endpoints
    val httpEndpoints = endpoints.map(HttpEndpoint.cast(_).get)

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(x: RequestHeader): Boolean = {
        logger.debug("[SmithyPlayRouter] calling isDefinedAt on service: " + service.id.name + "for path: " + x.path)
        httpEndpoints.exists(ep => checkIfRequestHeaderMatchesEndpoint(x, ep))
      }

      override def apply(v1: RequestHeader): Handler = {
        logger.debug("[SmithyPlayRouter] calling apply on: " + service.id.name)

        val validEndpoint = endpoints
          .filter(endpoint =>
            checkIfRequestHeaderMatchesEndpoint(
              v1,
              HttpEndpoint.cast(endpoint).get
            )
          )
          .head
        new SmithyPlayEndpoint(
          interpreter,
          validEndpoint,
          smithy4s.http.json.codecs(
            smithy4s.api.SimpleRestJson.protocol.hintMask ++ HintMask(
              InputOutput
            )
          )
        ).handler(v1)
      }

    }
  }

  private def checkIfRequestHeaderMatchesEndpoint(
      x: RequestHeader,
      ep: HttpEndpoint[_]
  ) = {

    matchRequestPath(x,ep).isDefined && x.method
      .equals(
        ep.method.showUppercase
      )

  }
}
