package de.innfactory.smithy4play.sbt

import java.io.{ File, PrintWriter }
import scala.util.Using

object RegistryGenerator {

  def generate(
    services: List[ScannedService],
    controllers: List[ScannedController],
    registryPackage: String,
    registryName: String,
    outputDir: File
  ): File = {
    val packageDir = new File(outputDir, registryPackage.replace('.', '/'))
    packageDir.mkdirs()

    val outputFile = new File(packageDir, s"$registryName.scala")

    val content = generateContent(services, controllers, registryPackage, registryName)

    Using(new PrintWriter(outputFile)) { writer =>
      writer.write(content)
    }

    outputFile
  }

  def generateContent(
    services: List[ScannedService],
    controllers: List[ScannedController],
    registryPackage: String,
    registryName: String
  ): String = {
    // Explicitly cast each service to Service[?] to avoid Scala 3 LUB computation
    // which can hit recursion limits with many services having complex type parameters
    val allServicesCode = services
      .sortBy(_.fullyQualifiedName)
      .map(s => s"    (${s.fullyQualifiedName}: Service[?])")
      .mkString(",\n")

    val controllerClassesCode = controllers
      .sortBy(_.fullyQualifiedName)
      .map(c => s"    classOf[${c.fullyQualifiedName}]")
      .mkString(",\n")

    s"""package $registryPackage
       |
       |import de.innfactory.smithy4play.routing.Smithy4PlayRegistry
       |import de.innfactory.smithy4play.routing.controller.AutoRoutableController
       |import smithy4s.Service
       |
       |object $registryName extends Smithy4PlayRegistry {
       |
       |  override val allServices: List[Service[?]] = List(
       |$allServicesCode
       |  )
       |
       |  override val controllerClasses: List[Class[? <: AutoRoutableController]] = List(
       |$controllerClassesCode
       |  )
       |}
       |""".stripMargin
  }
}
