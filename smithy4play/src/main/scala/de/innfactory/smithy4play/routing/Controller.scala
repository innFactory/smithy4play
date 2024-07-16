package de.innfactory.smithy4play.routing

import de.innfactory.smithy4play.ContextRoute
import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.routing.controller.AutoRoutableController
import de.innfactory.smithy4play.routing.internal.Smithy4PlayRouter
import de.innfactory.smithy4play.routing.middleware.Middleware
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes
import smithy4s.Service
import smithy4s.kinds.FunctorAlgebra

import scala.concurrent.ExecutionContext

trait Controller[Alg[_[_, _, _, _, _]]](implicit
  service: Service[Alg],
  cc: ControllerComponents,
  ec: ExecutionContext
) extends AutoRoutableController {
  self: FunctorAlgebra[Alg, ContextRoute] =>

  private def transform(
    impl: FunctorAlgebra[Alg, ContextRoute]
  ): (Codec, Middleware) => Routes = (codec, middleware) =>
    new Smithy4PlayRouter[Alg](
      impl,
      service,
      codec,
      middleware
    ).routes()

  def router: (Codec, Middleware) => Routes = transform(this)

}
