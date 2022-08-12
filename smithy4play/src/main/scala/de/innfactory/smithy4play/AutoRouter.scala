package de.innfactory.smithy4play

import com.typesafe.config.Config
import io.github.classgraph.{ClassGraph, ScanResult}
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Singleton
class AutoRouter @Inject(
) (implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
   config: Config
) extends BaseRouter {

  override val controllers: Seq[Routes] = {
    val pkg = config.getString("smithy4play.autoRoutePackage")
    val classGraphScanner: ScanResult = new ClassGraph().enableAllInfo().acceptPackages(pkg).scan()
    val controllers                   = classGraphScanner.getClassesImplementing(classOf[AutoRoutableController])
    logger.debug(s"[AutoRouter] found ${controllers.size()} Controllers")
    classGraphScanner.close()
    controllers.asScala.map(_.loadClass(true)).map(clazz => createFromClass(clazz)).toSeq
  }

  def createFromClass(clazz: Class[_]): Routes =
    app.injector.instanceOf(clazz) match {
      case c: AutoRoutableController => c.routes
    }

}
