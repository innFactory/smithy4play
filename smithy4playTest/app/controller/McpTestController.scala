package controller

import cats.data.{ EitherT, Kleisli }
import de.innfactory.smithy4play.ContextRoute
import de.innfactory.smithy4play.routing.Controller
import play.api.libs.ws.WSClient
import play.api.mvc.ControllerComponents
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import testDefinitions.test.*
import testDefinitions.test.McpControllerServiceGen.serviceInstance

@Singleton
class McpTestController @Inject() (implicit
  cc: ControllerComponents,
  ec: ExecutionContext,
  ws: WSClient
) extends McpControllerService[ContextRoute]
    with Controller {

  override def reverseString(text: String): ContextRoute[ReverseStringOutput] = Kleisli { rc =>
    val reversed = text.reverse
    EitherT.rightT[Future, Throwable](ReverseStringOutput(reversed))
  }
}
