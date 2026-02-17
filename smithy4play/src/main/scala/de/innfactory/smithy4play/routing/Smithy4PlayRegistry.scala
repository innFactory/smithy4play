package de.innfactory.smithy4play.routing

import de.innfactory.smithy4play.routing.controller.AutoRoutableController
import smithy4s.Service

trait Smithy4PlayRegistry {
  def allServices: List[Service[?]]
  def controllerClasses: List[Class[? <: AutoRoutableController]]
}

object Smithy4PlayRegistry {

  def load(className: String): Smithy4PlayRegistry = {
    val objectClassName = if (className.endsWith("$")) className else className + "$"
    val clazz           = Class.forName(objectClassName, true, Thread.currentThread().getContextClassLoader)
    clazz.getField("MODULE$").get(null).asInstanceOf[Smithy4PlayRegistry]
  }
}
