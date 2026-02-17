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

  def reverseString(
    text: String,
    taggedTestUnion: Option[TaggedTestUnion],
    untaggedTestUnion: Option[UntaggedTestUnion],
    discriminatedTestUnion: Option[DiscriminatedTestUnion]
  ): ContextRoute[ReverseStringOutput] = Kleisli { rc =>
    val reversed = text.reverse
    EitherT.rightT[Future, Throwable](ReverseStringOutput(reversed, reversed.replaceAll("\\s", "").length * 2))
  }

  def hiddenOperation(value: String): ContextRoute[HiddenOperationOutput] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](HiddenOperationOutput(value))
  }
}
