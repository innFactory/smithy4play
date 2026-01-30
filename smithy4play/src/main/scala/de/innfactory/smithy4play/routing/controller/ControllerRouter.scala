package de.innfactory.smithy4play.routing.controller

import com.typesafe.config.Config
import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.logger
import de.innfactory.smithy4play.routing.internal.{ BaseRouter, InternalRoute }
import de.innfactory.smithy4play.routing.middleware.Middleware
import play.api.Application
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

/**
 * Base router that automatically discovers and registers controllers.
 * 
 * Controllers are discovered by scanning the package specified in 
 * `smithy4play.autoRoutePackage` configuration. Scan results are cached
 * in [[ControllerRegistry]] to avoid repeated classpath scanning.
 */
private[smithy4play] abstract class ControllerRouter(implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends BaseRouter
    with Codec
    with Middleware {

  private val pkg = config.getString("smithy4play.autoRoutePackage")

  protected val controllers: Seq[InternalRoute] = {
    // Use cached registry instead of scanning every time
    val controllerClasses = ControllerRegistry.getControllers(pkg)
    logger.debug(s"[ControllerRouter] Using ${controllerClasses.size} controllers from registry")
    
    controllerClasses.map(clazz => mapControllerToRoutes(clazz)(this, this))
  }

  private def mapControllerToRoutes(clazz: Class[?]): (Codec, Middleware) => InternalRoute =
    app.injector.instanceOf(clazz) match {
      case c: AutoRoutableController => c.router
    }
}

