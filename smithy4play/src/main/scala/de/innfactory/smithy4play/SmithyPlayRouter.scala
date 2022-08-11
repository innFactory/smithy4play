package de.innfactory.smithy4play

import play.api.mvc.{ ControllerComponents, Handler, RequestHeader }
import play.api.routing.Router.Routes
import smithy4s.http.{ matchPath, HttpEndpoint, HttpMethod, PathSegment }
import smithy4s.{ Endpoint, GenLift, HintMask, Monadic, Service, Transformation }
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

    val service: Service[Alg, Op]                     = serviceProvider.service
    val interpreter: Transformation[Op, GenLift[F]#λ] = service.asTransformation[GenLift[F]#λ](impl)
    val endpoints: Seq[Endpoint[Op, _, _, _, _, _]]   = service.endpoints
    val httpEndpoints                                 = endpoints.map(HttpEndpoint.cast(_).get)

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(x: RequestHeader): Boolean = {
        logger.debug("[SmithyPlayRouter] calling isDefinedAt on service: " + service.id.name + "for path: " + x.path)
        httpEndpoints.exists(ep => checkIfRequestHeaderMatchesEndpoint(x, ep))
      }

      override def apply(v1: RequestHeader): Handler = {
        logger.debug("[SmithyPlayRouter] calling apply on: " + service.id.name)

        val validEndpoint = endpoints.find(endpoint =>
          checkIfRequestHeaderMatchesEndpoint(
            v1,
            HttpEndpoint.cast(endpoint).get
          )
        )
        new SmithyPlayEndpoint(
          interpreter,
          validEndpoint.get,
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
    ep.path.foreach {
      case PathSegment.StaticSegment(value) =>
        if (value.contains(" "))
          logger.info("following pathSegment contains a space: " + value)
      case PathSegment.LabelSegment(value)  =>
        if (value.contains(" "))
          logger.info("following pathSegment contains a space: " + value)
      case PathSegment.GreedySegment(value) =>
        if (value.contains(" "))
          logger.info("following pathSegment contains a space: " + value)
    }
    matchRequestPath(x, ep).isDefined && x.method
      .equals(
        ep.method.showUppercase
      )
  }
}
