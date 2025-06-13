package de.innfactory.smithy4play

import cats.implicits.toTraverseOps
import play.api.mvc.{ AbstractController, ControllerComponents, Handler, RequestHeader }
import play.api.routing.Router.Routes
import smithy4s.http.{ HttpEndpoint, PathSegment }
import smithy4s.{ Endpoint, HintMask, Service }
import smithy4s.internals.InputOutput
import smithy4s.kinds.{ BiFunctorAlgebra, FunctorAlgebra, FunctorInterpreter, Kind1, PolyFunction5 }

import scala.concurrent.ExecutionContext

class SmithyPlayRouter[Alg[_[_, _, _, _, _]], F[
  _
] <: ContextRoute[_]](
  impl: FunctorAlgebra[Alg, F]
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends AbstractController(cc) {

  def routes()(implicit
    service: smithy4s.Service[Alg]
  ): Routes = {

    val interpreter: PolyFunction5[service.Operation, Kind1[F]#toKind5]             = service.toPolyFunction[Kind1[F]#toKind5](impl)
    val endpoints: Seq[service.Endpoint[_, _, _, _, _]]                             = service.endpoints
    val httpEndpoints: Seq[Either[HttpEndpoint.HttpEndpointError, HttpEndpoint[_]]] =
      endpoints.map(HttpEndpoint.cast(_))

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(x: RequestHeader): Boolean = {
        logger.debug("[SmithyPlayRouter] calling isDefinedAt on service: " + service.id.name + " for path: " + x.path)
        httpEndpoints.exists(ep => ep.exists(checkIfRequestHeaderMatchesEndpoint(x, _)))
      }

      override def apply(v1: RequestHeader): Handler = {
        logger.debug("[SmithyPlayRouter] calling apply on: " + service.id.name)
        for {
          zippedEndpoints         <- endpoints.map(ep => HttpEndpoint.cast(ep).map((ep, _))).sequence
          endpointAndHttpEndpoint <-
            zippedEndpoints
              .find(ep => checkIfRequestHeaderMatchesEndpoint(v1, ep._2))
              .toRight(
                HttpEndpoint.HttpEndpointError("Could not cast Endpoint to HttpEndpoint, likely a bug in smithy4s")
              )
        } yield new SmithyPlayEndpoint(
          service,
          interpreter,
          endpointAndHttpEndpoint._1,
          smithy4s.http.json.codecs(alloy.SimpleRestJson.protocol.hintMask ++ HintMask(InputOutput), 4096)
        ).handler(v1)
      } match {
        case Right(value) => value
        case Left(value)  => throw new Exception(value.message)
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
