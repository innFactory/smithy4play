package de.innfactory.smithy4play

import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes
import smithy4s.kinds.FunctorAlgebra

import scala.concurrent.ExecutionContext

trait AutoRoutableController {

  implicit def transformToRouter[Alg[_[_, _, _, _, _]], F[
    _
  ] <: ContextRoute[_]](
    impl: FunctorAlgebra[Alg, F]
  )(implicit
    service: smithy4s.Service[Alg],
    ec: ExecutionContext,
    cc: ControllerComponents
  ): Seq[MiddlewareBase] => Routes = (middlewares: Seq[MiddlewareBase]) =>
    new SmithyPlayRouter[Alg, F](impl, service).routes(middlewares)

  val router: Seq[MiddlewareBase] => Routes

}
