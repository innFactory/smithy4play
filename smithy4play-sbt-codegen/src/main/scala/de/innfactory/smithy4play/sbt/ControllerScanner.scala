package de.innfactory.smithy4play.sbt

import io.github.classgraph.{ ClassGraph, ClassInfo, ScanResult }

import java.io.File
import scala.jdk.CollectionConverters.*

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
    } finally
      if (scanResult != null) {
        scanResult.close()
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
    // Strategy: Find the smithy4s service trait that this controller implements.
    // The service trait ends with "Gen" and is parameterized with ContextRoute (erased to ? in bytecode).
    val typeSignature = classInfo.getTypeSignature
    if (typeSignature == null) return None

    val superInterfaceSignatures = typeSignature.getSuperinterfaceSignatures
    if (superInterfaceSignatures == null) return None

    // Strategy 1: Look for interface ending with "Gen" that has type arguments (ContextRoute erased to ?)
    // Example: BemaControllerGen<?> where ? is ContextRoute
    val serviceFromInterface = superInterfaceSignatures.asScala
      .find { sig =>
        val fqn      = sig.getFullyQualifiedClassName
        val typeArgs = sig.getTypeArguments
        fqn.endsWith("Gen") && typeArgs != null && !typeArgs.isEmpty
      }
      .map(_.getFullyQualifiedClassName)

    if (serviceFromInterface.isDefined) {
      return serviceFromInterface
    }

    // Strategy 2: Look for superclass like BaseController<ServiceGen> where type arg ends with "Gen"
    val superclassSignature = typeSignature.getSuperclassSignature
    if (superclassSignature != null) {
      val superTypeArgs = superclassSignature.getTypeArguments
      if (superTypeArgs != null && !superTypeArgs.isEmpty) {
        val serviceFromSuperclass = superTypeArgs.asScala
          .map(_.toString)
          .find(_.endsWith("Gen"))

        if (serviceFromSuperclass.isDefined) {
          return serviceFromSuperclass
        }
      }
    }

    // Strategy 3: Fallback - look for Controller<ServiceGen> in interfaces
    superInterfaceSignatures.asScala
      .find(_.getFullyQualifiedClassName == "de.innfactory.smithy4play.routing.Controller")
      .flatMap { controllerSig =>
        val typeArgs = controllerSig.getTypeArguments
        if (typeArgs != null && !typeArgs.isEmpty) {
          Some(typeArgs.get(0).toString)
        } else {
          None
        }
      }
  }

  def matchControllersToServices(
    controllers: List[ScannedController],
    services: List[ScannedService]
  ): List[(ScannedController, Option[ScannedService])] =
    controllers.map { controller =>
      // The serviceTraitName is fully qualified (e.g., io.example.MyServiceGen)
      // Match it exactly against the service's fully qualified name
      val matchingService = services.find { service =>
        controller.serviceTraitName == service.fullyQualifiedName
      }
      (controller, matchingService)
    }
}
