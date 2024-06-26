package de.innfactory.smithy4play

import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes
import smithy4s.kinds.FunctorAlgebra

import scala.concurrent.ExecutionContext

trait AutoRoutableController {

  implicit def transformToRouter[Alg[_[_, _, _, _, _]]](
    impl: FunctorAlgebra[Alg, FutureMonadError]
  )(implicit
    service: smithy4s.Service[Alg],
    ec: ExecutionContext,
    cc: ControllerComponents
  ): Routes = new SmithyPlayRouter[Alg](impl, service).handler

  val router: Routes

}
