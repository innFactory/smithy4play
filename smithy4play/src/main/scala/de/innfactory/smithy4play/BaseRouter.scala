package de.innfactory.smithy4play

import play.api.mvc.{ControllerComponents, Handler, RequestHeader}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import smithy4s.kinds.FunctorAlgebra

import scala.concurrent.ExecutionContext

abstract class BaseRouter(implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext
) extends SimpleRouter {

  implicit def transformToRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
    _
  ] <: ContextRoute[_]](
    impl: FunctorAlgebra[Alg, F]
  )(implicit
    serviceProvider: smithy4s.Service.Provider[Alg, Op],
    cc: ControllerComponents,
    executionContext: ExecutionContext
  ): Routes =
    new SmithyPlayRouter[Alg, Op, F](impl).routes()

  def chain(
    toChain: Seq[Routes]
  ): PartialFunction[RequestHeader, Handler] =
    toChain.foldLeft(PartialFunction.empty[RequestHeader, Handler])((a, b) => a orElse b)

  val controllers: Seq[Routes]

  def chainedRoutes: Routes = chain(controllers)

  override def routes: Routes = chainedRoutes

}
