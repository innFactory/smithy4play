package de.innfactory.smithy4play.routing.internal

import de.innfactory.smithy4play.RoutingResult
import de.innfactory.smithy4play.routing.TestClass
import de.innfactory.smithy4play.routing.context.RoutingContextBase
import de.innfactory.smithy4play.telemetry.Telemetry
import io.opentelemetry.api.trace.{ Span, SpanKind }
import io.opentelemetry.context.Context
import play.api.mvc.{ ControllerComponents, Handler, RequestHeader }
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.mvc
import play.api.mvc.*

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

abstract class BaseRouter(implicit
  cc: ControllerComponents,
  val executionContext: ExecutionContext
) extends AbstractController(cc)
    with SimpleRouter {

  val testClass = new TestClass

  private def chain(
    toChain: Seq[InternalRoute]
  ): InternalRoute =
    toChain.foldLeft(PartialFunction.empty[RequestHeader, Request[RawBuffer] => RoutingResult[Result]])((a, b) =>
      a orElse b
    )

  protected val controllers: Seq[InternalRoute]

  private def chainedRoutes: InternalRoute = chain(controllers)

  override def routes: Routes = internalHandler

  def applyHandler(v1: RequestHeader, request: Request[RawBuffer]): Future[Result] =
    if (chainedRoutes.isDefinedAt(v1)) {
      testClass.test(v1.path)
      chainedRoutes(v1)(request).value.map {
        case Left(value)  =>
          Results.Status(500)
        case Right(value) =>
          value
      }
    } else {
      Future(Results.Status(404))
    }

  private def internalHandler = new PartialFunction[RequestHeader, Handler] {
    override def isDefinedAt(x: RequestHeader): Boolean = true

    override def apply(v1: RequestHeader): Handler = Action.async(parse.raw) { implicit request =>
      applyHandler(v1, request)
    }

  }

}
