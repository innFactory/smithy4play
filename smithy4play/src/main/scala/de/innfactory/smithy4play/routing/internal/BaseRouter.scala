package de.innfactory.smithy4play.routing.internal

import cats.data.EitherT
import de.innfactory.smithy4play.RoutingResult
import de.innfactory.smithy4play.routing.context.RoutingContextBase
import play.api.mvc.{ControllerComponents, Handler, RequestHeader}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

/**
 * Base router that chains multiple controller routes together.
 */
abstract class BaseRouter(implicit
  cc: ControllerComponents,
  val executionContext: ExecutionContext
) extends AbstractController(cc)
    with SimpleRouter {

  /**
   * Chain multiple partial functions into a single route.
   * Routes are tried in order - first match wins.
   */
  private def chain(
    toChain: Seq[InternalRoute]
  ): InternalRoute =
    toChain.foldLeft(PartialFunction.empty[RequestHeader, Request[RawBuffer] => RoutingResult[Result]])((a, b) =>
      a orElse b
    )

  /**
   * Override to provide the controller routes to chain.
   */
  protected val controllers: Seq[InternalRoute]

  private lazy val chainedRoutes: InternalRoute = chain(controllers)

  override def routes: Routes = internalHandler

  /**
   * Apply the matched handler to process the request.
   */
  private def applyInternalHandler(v1: RequestHeader, request: Request[RawBuffer]): Future[Result] = {
    val handler = chainedRoutes.applyOrElse(
        v1,
        (_: RequestHeader) => (_: Request[RawBuffer]) => EitherT.leftT[Future, Result](PathNotFound())
    )

    handler.apply(request).value.map {
      case Left(value)  =>
        value match {
          case PathNotFound() => Results.Status(404)
          case _            => Results.Status(500)
        }
      case Right(value) => value
    }
  }

  private def internalHandler: PartialFunction[RequestHeader, Handler] = new PartialFunction[RequestHeader, Handler] {
    override def isDefinedAt(x: RequestHeader): Boolean = true

    override def apply(v1: RequestHeader): Handler = Action.async(parse.raw) { implicit request =>
      applyInternalHandler(v1, request)
    }
  }

}
