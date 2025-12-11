package de.innfactory.smithy4play

import com.typesafe.config.Config
import de.innfactory.smithy4play.routing.controller.ControllerRouter
import play.api.Application
import play.api.mvc.ControllerComponents

import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext

@Singleton
class AutoRouter @Inject() (implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends ControllerRouter
