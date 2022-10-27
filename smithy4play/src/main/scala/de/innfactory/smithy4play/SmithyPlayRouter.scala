package de.innfactory.smithy4play

import cats.implicits.toTraverseOps
import play.api.mvc.{AbstractController, ControllerComponents, Handler, RequestHeader}
import play.api.routing.Router.Routes
import smithy4s.http.{HttpEndpoint, PathSegment}
import smithy4s.{Endpoint, HintMask, Service}
import smithy4s.internals.InputOutput
import smithy4s.kinds.{BiFunctorAlgebra, FunctorAlgebra, FunctorInterpreter, Kind1}

import scala.concurrent.ExecutionContext

class SmithyPlayRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
  _
] <: ContextRoute[_]](
  impl: FunctorAlgebra[Alg, F]
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends AbstractController(cc) {

  def routes()(implicit
    serviceProvider: smithy4s.Service.Provider[Alg, Op]
  ): Routes = {

    val service: Service[Alg, Op]                   = serviceProvider.service
    val interpreter: FunctorInterpreter[Op, F]             = service.toPolyFunction[Kind1[F]#toKind5](impl)
    val endpoints: Seq[Endpoint[Op, _, _, _, _, _]] = service.endpoints
    val httpEndpoints: Seq[Option[HttpEndpoint[_]]] = endpoints.map(HttpEndpoint.cast(_))

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(x: RequestHeader): Boolean = {
        logger.debug("[SmithyPlayRouter] calling isDefinedAt on service: " + service.id.name + " for path: " + x.path)
        httpEndpoints.exists(ep => ep.exists(checkIfRequestHeaderMatchesEndpoint(x, _)))
      }

      override def apply(v1: RequestHeader): Handler = {
        logger.debug("[SmithyPlayRouter] calling apply on: " + service.id.name)
        for {
          zippedEndpoints         <- endpoints.map(ep => HttpEndpoint.cast(ep).map((ep, _))).sequence
          endpointAndHttpEndpoint <- zippedEndpoints.find(ep => checkIfRequestHeaderMatchesEndpoint(v1, ep._2))
        } yield new SmithyPlayEndpoint(
          service,
          interpreter,
          endpointAndHttpEndpoint._1,
          smithy4s.http.json.codecs(smithy4s.api.SimpleRestJson.protocol.hintMask ++ HintMask(InputOutput))
        ).handler(v1)
      } match {
        case Some(value) => value
        case None        => throw new Exception("Could not cast Endpoint to HttpEndpoint, likely a bug in smithy4s")
      }

    }
  }

  private def checkIfRequestHeaderMatchesEndpoint(
    x: RequestHeader,
    ep: HttpEndpoint[_]
  ) = {
    ep.path.map {
      case PathSegment.StaticSegment(value) => value
      case PathSegment.LabelSegment(value)  => value
      case PathSegment.GreedySegment(value) => value
    }
      .filter(_.contains(" "))
      .foreach(value => logger.info("following pathSegment contains a space: " + value))
    matchRequestPath(x, ep).isDefined && x.method == ep.method.showUppercase
  }
}
