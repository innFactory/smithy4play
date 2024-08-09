package controller

import com.typesafe.config.Config
import controller.middlewares.ValidateAuthMiddleware
import controller.middlewares.AddHeaderMiddleware
import de.innfactory.smithy4play.AutoRouter
import de.innfactory.smithy4play.routing.middleware.Smithy4PlayMiddleware
import play.api.Application
import play.api.mvc.ControllerComponents
import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext

@Singleton
class TestRouter @Inject() (implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends AutoRouter {

  override def smithy4PlayMiddleware: Seq[Smithy4PlayMiddleware] =
    super.smithy4PlayMiddleware ++ Seq(ValidateAuthMiddleware(), AddHeaderMiddleware())

}
