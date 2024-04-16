package de.innfactory.smithy4play

import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import de.innfactory.smithy4play.middleware.MiddlewareBase
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
  ): (Seq[MiddlewareBase], ReaderConfig) => Routes = (middlewares: Seq[MiddlewareBase], readerConfig: ReaderConfig) =>
    new SmithyPlayRouter[Alg, F](impl, service).routes(middlewares, readerConfig)

  val router: (Seq[MiddlewareBase], ReaderConfig) => Routes

}
