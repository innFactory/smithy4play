package controller

import cats.data.{ EitherT, Kleisli }
import cats.implicits.catsSyntaxEitherId
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import de.innfactory.smithy4play.middleware.MiddlewareBase
import de.innfactory.smithy4play.{ AutoRoutableController, ContextRoute, ContextRouteError }
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes
import testDefinitions.test.{ XmlControllerDef, XmlTestInputBody, XmlTestOutput, XmlTestWithInputAndOutputOutput }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class XmlController @Inject() (implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext
) extends XmlControllerDef[ContextRoute]
    with AutoRoutableController {
  override val router: (Seq[MiddlewareBase], ReaderConfig) => Routes = this

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
            .asRight[ContextRouteError]
        )
      )
    }
}
