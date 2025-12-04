package controller

import cats.data.{ EitherT, Kleisli }
import cats.implicits.catsSyntaxEitherId
import de.innfactory.smithy4play.ContextRoute
import de.innfactory.smithy4play.routing.Controller
import play.api.mvc.ControllerComponents
import testDefinitions.test.{ XmlControllerDef, XmlTestInputBody, XmlTestOutput, XmlTestWithInputAndOutputOutput }
import XmlControllerDef.serviceInstance
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class XmlController @Inject() (implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext
) extends XmlControllerDef[ContextRoute]
    with Controller {

  override def xmlTestWithInputAndOutput(
    xmlTest: String,
    body: XmlTestInputBody
  ): ContextRoute[XmlTestWithInputAndOutputOutput] =
    Kleisli { _ =>
      EitherT(
        Future(
          XmlTestWithInputAndOutputOutput(
            XmlTestOutput(body.serverzeit, body.requiredTest + xmlTest, body.requiredInt.map(i => i * i))
          )
            .asRight[Throwable]
        )
      )
    }
}
