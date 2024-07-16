package de.innfactory.smithy4play.routing.controller

import com.typesafe.config.Config
import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.logger
import de.innfactory.smithy4play.routing.internal.BaseRouter
import de.innfactory.smithy4play.routing.middleware.Middleware
import io.github.classgraph.{ ClassGraph, ScanResult }
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

private[smithy4play] abstract class ControllerRouter(implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends BaseRouter
    with Codec
    with Middleware {

  private val pkg = config.getString("smithy4play.autoRoutePackage")

  protected val controllers: Seq[Routes] = {
    val classGraphScanner: ScanResult = new ClassGraph().enableAllInfo().acceptPackages(pkg).scan()
    val controllers                   = classGraphScanner.getClassesImplementing(classOf[AutoRoutableController])
    logger.debug(s"[AutoRouter] found ${controllers.size().toString} controllers")
    val filteredControllers           = controllers.asScala.filter(!_.isAbstract).map(_.loadClass(true))
    val routes: Seq[Routes]           = filteredControllers.map(clazz => mapControllerToRoutes(clazz)(this, this)).toSeq
    classGraphScanner.close()
    routes
  }

  private def mapControllerToRoutes(clazz: Class[?]): (Codec, Middleware) => Routes =
    app.injector.instanceOf(clazz) match {
      case c: AutoRoutableController => c.router
    }

}
