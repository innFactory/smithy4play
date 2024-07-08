package de.innfactory.smithy4play.routing

import play.api.mvc.{ControllerComponents, Handler, RequestHeader}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter

import scala.concurrent.ExecutionContext

private[smithy4play] abstract class BaseRouter(implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext
) extends SimpleRouter {
  
  private def chain(
    toChain: Seq[Routes]
  ): PartialFunction[RequestHeader, Handler] =
    toChain.foldLeft(PartialFunction.empty[RequestHeader, Handler])((a, b) => a orElse b)

  protected val controllers: Seq[Routes]

  private def chainedRoutes: Routes = chain(controllers)

  override def routes: Routes = chainedRoutes

}
