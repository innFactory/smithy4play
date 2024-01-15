import com.google.inject.AbstractModule
import controller.middlewares.MiddlewareRegistry
import de.innfactory.smithy4play.middleware.MiddlewareRegistryBase
import play.api.libs.concurrent.PekkoGuiceSupport

/** This module handles the bindings for the API to the Slick implementation.
  *
  * https://www.playframework.com/documentation/latest/ScalaDependencyInjection#Programmatic-bindings
  */
class Module extends AbstractModule with PekkoGuiceSupport {

  override def configure(): Unit =
    bind(classOf[MiddlewareRegistryBase]).to(classOf[MiddlewareRegistry])

}
