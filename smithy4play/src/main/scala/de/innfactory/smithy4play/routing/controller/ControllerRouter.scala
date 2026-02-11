package de.innfactory.smithy4play.routing.controller

import com.typesafe.config.Config
import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.logger
import de.innfactory.smithy4play.routing.Smithy4PlayRegistry
import de.innfactory.smithy4play.routing.internal.{ BaseRouter, InternalRoute }
import de.innfactory.smithy4play.routing.middleware.Middleware
import play.api.Application
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

private[smithy4play] abstract class ControllerRouter(implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends BaseRouter
    with Codec
    with Middleware {

  private val registryClassName = config.getString("smithy4play.registry")

  protected val controllers: Seq[InternalRoute] = {
    val registry          = Smithy4PlayRegistry.load(registryClassName)
    val controllerClasses = registry.controllerClasses
    logger.debug(s"[ControllerRouter] Using ${controllerClasses.size} controllers from registry")

    controllerClasses.map(clazz => mapControllerToRoutes(clazz)(this, this))
  }

  private def mapControllerToRoutes(clazz: Class[? <: AutoRoutableController]): (Codec, Middleware) => InternalRoute =
    app.injector.instanceOf(clazz).router
}
