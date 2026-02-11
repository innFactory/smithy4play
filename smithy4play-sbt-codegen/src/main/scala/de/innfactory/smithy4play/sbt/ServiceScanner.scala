package de.innfactory.smithy4play.sbt

import io.github.classgraph.{ ClassGraph, ClassInfo, ScanResult }
import java.io.File
import scala.jdk.CollectionConverters._

case class ScannedService(
  objectName: String,
  packageName: String
) {
  def fullyQualifiedName: String = s"$packageName.$objectName"
}

object ServiceScanner {

  def scan(classpathEntries: Seq[File]): List[ScannedService] = {
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

      val serviceClasses = scanResult
        .getClassesImplementing("smithy4s.Service")
        .asScala
        .toList
        .filter(_.getName.endsWith("Gen$"))

      serviceClasses.flatMap(classInfoToService)
    } finally
      if (scanResult != null) {
        scanResult.close()
      }
  }

  private def classInfoToService(classInfo: ClassInfo): Option[ScannedService] = {
    val fullName = classInfo.getName

    if (!fullName.endsWith("$")) {
      return None
    }

    val objectName  = fullName.split('.').last.stripSuffix("$")
    val packageName = fullName.split('.').dropRight(1).mkString(".")

    Some(
      ScannedService(
        objectName = objectName,
        packageName = packageName
      )
    )
  }
}
