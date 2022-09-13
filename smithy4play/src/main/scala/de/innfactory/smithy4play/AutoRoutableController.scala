package de.innfactory.smithy4play

import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes
import smithy4s.Monadic

import scala.concurrent.ExecutionContext

trait AutoRoutableController {

  implicit def transformToRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
    _
  ] <: ContextRoute[_]](
    impl: Monadic[Alg, F]
  )(implicit
    serviceProvider: smithy4s.Service.Provider[Alg, Op],
    ec: ExecutionContext,
    cc: ControllerComponents
  ): Routes =
    new SmithyPlayRouter[Alg, Op, F](impl).routes()

  val routes: Routes

}
