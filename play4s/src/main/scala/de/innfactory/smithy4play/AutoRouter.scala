package de.innfactory.smithy4play

import io.github.classgraph.{ClassGraph, ScanResult}
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

class AutoRouter @Inject(
) (implicit
   cc: ControllerComponents,
   app: Application,
   ec: ExecutionContext
  ) extends BaseRouter {

  override val controllers: Seq[Routes] = {
    val x: ScanResult = new ClassGraph().verbose().enableAllInfo().scan()
    val y = x.getClassesImplementing(classOf[AutoRoutableController])
    y.asScala.map(_.loadClass(true)).map(clazz => createFromClass(clazz)).toSeq
  }

  def createFromClass(clazz: Class[_]): Routes = {
    app.injector.instanceOf(clazz) match {
      case c: AutoRoutableController => c.routes
    }
  }

}
