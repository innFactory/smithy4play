package controller

import cats.data.{ EitherT, Kleisli }
import cats.implicits.catsSyntaxEitherId
import de.innfactory.smithy4play.{ AutoRouting, ContextRoute, ContextRouteError }
import play.api.mvc.ControllerComponents
import testDefinitions.test.{ XmlControllerDef, XmlTestInputBody, XmlTestOutput, XmlTestWithInputAndOutputOutput }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
@AutoRouting
class XmlController @Inject() (implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext
) extends XmlControllerDef[ContextRoute] {

  override def xmlTestWithInputAndOutput(
    xmlTest: String,
    body: XmlTestInputBody
  ): ContextRoute[XmlTestWithInputAndOutputOutput] =
    Kleisli { _ =>
      EitherT(
        Future(
          XmlTestWithInputAndOutputOutput(
            "application/xml",
            XmlTestOutput(body.requiredTest + xmlTest, body.requiredInt.map(i => i * i))
          )
            .asRight[ContextRouteError]
        )
      )
    }
}
