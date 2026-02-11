package de.innfactory.smithy4play.sbt

import io.github.classgraph.{ClassGraph, ClassInfo, ScanResult}
import java.io.File
import scala.jdk.CollectionConverters._

case class ScannedController(
  className: String,
  packageName: String,
  serviceTraitName: String
) {
  def fullyQualifiedName: String = s"$packageName.$className"
}

object ControllerScanner {

  def scan(classpathEntries: Seq[File]): List[ScannedController] = {
    val existingPaths = classpathEntries.filter(_.exists()).map(_.getAbsolutePath)
    if (existingPaths.isEmpty) {
      return Nil
    }

    var scanResult: ScanResult = null
    try {
      scanResult = new ClassGraph()
        .overrideClasspath(existingPaths: _*)
        .enableClassInfo()
        .scan()

      val controllerClasses = scanResult
        .getClassesImplementing("de.innfactory.smithy4play.routing.controller.AutoRoutableController")
        .asScala
        .toList

      val controllerTraitClasses = scanResult
        .getClassesImplementing("de.innfactory.smithy4play.routing.Controller")
        .asScala
        .toList

      val allControllerClasses = (controllerClasses ++ controllerTraitClasses)
        .groupBy(_.getName)
        .values
        .map(_.head)
        .toList

      allControllerClasses.flatMap(classInfoToController)
    } finally {
      if (scanResult != null) {
        scanResult.close()
      }
    }
  }

  private def classInfoToController(classInfo: ClassInfo): Option[ScannedController] = {
    if (classInfo.isInterface || classInfo.isAbstract) {
      return None
    }

    val fullName    = classInfo.getName
    val className   = fullName.split('.').last
    val packageName = fullName.split('.').dropRight(1).mkString(".")

    val serviceTraitName = findServiceTraitName(classInfo)

    serviceTraitName.map { traitName =>
      ScannedController(
        className = className,
        packageName = packageName,
        serviceTraitName = traitName
      )
    }
  }

  private def findServiceTraitName(classInfo: ClassInfo): Option[String] = {
    val interfaces = classInfo.getInterfaces.asScala.toList
    val superclasses = classInfo.getSuperclasses.asScala.toList

    val allTypes = interfaces ++ superclasses

    allTypes
      .map(_.getName)
      .find { name =>
        (name.contains("Service") || name.contains("Def")) &&
        !name.contains("AutoRoutableController") &&
        !name.contains("routing.Controller")
      }
      .map(_.split('.').last.split('$').head)
  }

  def matchControllersToServices(
    controllers: List[ScannedController],
    services: List[ScannedService]
  ): List[(ScannedController, Option[ScannedService])] =
    controllers.map { controller =>
      val matchingService = services.find { service =>
        val serviceBaseName = service.objectName.stripSuffix("Gen")
        controller.serviceTraitName == serviceBaseName ||
        controller.serviceTraitName + "Gen" == service.objectName ||
        controller.serviceTraitName == service.objectName
      }
      (controller, matchingService)
    }
}
