package de.innfactory.smithy4play

import org.reflections.Reflections
import org.reflections.util.ClasspathHelper
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Singleton
class AutoRouter @Inject(
) (implicit
    cc: ControllerComponents,
    app: Application,
    ec: ExecutionContext
) extends BaseRouter {
  val reflection = new Reflections();

  override val controllers: Seq[Routes] = {
    val x = reflection
      .getSubTypesOf(classOf[AutoRoutableController])
      //.getTypesAnnotatedWith(classOf[Singleton])
      .asScala
    println(x)

    x.map(clazz => {
      println(clazz)
      createFromClass(clazz)
    }).toSeq
  }

  def createFromClass(clazz: Class[_]): Routes = {
    app.injector.instanceOf(clazz) match {
      case c: AutoRoutableController => c.routes
    }
  }

}
