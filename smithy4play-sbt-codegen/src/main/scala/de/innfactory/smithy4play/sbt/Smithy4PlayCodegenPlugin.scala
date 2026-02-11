package de.innfactory.smithy4play.sbt

import sbt._
import sbt.Keys._
import scala.sys.process._

object Smithy4PlayCodegenPlugin extends AutoPlugin {

  object autoImport {
    val smithy4playGenerateRegistry  = taskKey[File]("Generate the Smithy4Play registry")
    val smithy4playRegistryPackage   = settingKey[String]("Package for the generated registry")
    val smithy4playRegistryName      = settingKey[String]("Name of the generated registry object")
    val smithy4playRegistryOutputDir = settingKey[File]("Output directory for the generated registry")
  }

  import autoImport._

  override def trigger = noTrigger

  override def requires = plugins.JvmPlugin

  private def generateAndCompileRegistry(
    log: sbt.util.Logger,
    classesDir: File,
    registryPackage: String,
    registryName: String,
    outputDir: File,
    fullClasspath: Seq[File],
    scalaInst: xsbti.compile.ScalaInstance
  ): Unit = {
    val registryClassFile = new File(
      classesDir,
      registryPackage.replace('.', '/') + "/" + registryName + "$.class"
    )

    if (!registryClassFile.exists()) {
      log.info(s"[smithy4play-codegen] Registry class not found, generating and compiling...")

      val allClasspathEntries = classesDir +: fullClasspath

      val services = ServiceScanner.scan(allClasspathEntries)
      log.info(s"[smithy4play-codegen] Found ${services.size} services")
      services.foreach(s => log.info(s"[smithy4play-codegen]   - ${s.fullyQualifiedName}"))

      val controllers = ControllerScanner.scan(allClasspathEntries)
      log.info(s"[smithy4play-codegen] Found ${controllers.size} controllers")
      controllers.foreach(c => log.info(s"[smithy4play-codegen]   - ${c.fullyQualifiedName}"))

      val matched = ControllerScanner.matchControllersToServices(controllers, services)
      matched.foreach { case (controller, serviceOpt) =>
        serviceOpt match {
          case Some(service) =>
            log.info(s"[smithy4play-codegen] Matched ${controller.className} -> ${service.objectName}")
          case None =>
            log.warn(
              s"[smithy4play-codegen] Controller ${controller.className} has no matching service (trait: ${controller.serviceTraitName})"
            )
        }
      }

      val outputFile = RegistryGenerator.generate(
        services = services,
        controllers = controllers,
        registryPackage = registryPackage,
        registryName = registryName,
        outputDir = outputDir
      )

      log.info(s"[smithy4play-codegen] Generated registry source: $outputFile")

      val classpathStr = (classesDir +: fullClasspath).map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

      val compileCmd = Seq(
        "java",
        "-cp", scalaInst.allJars.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator),
        "dotty.tools.dotc.Main",
        "-classpath", classpathStr,
        "-d", classesDir.getAbsolutePath,
        outputFile.getAbsolutePath
      )

      log.info(s"[smithy4play-codegen] Compiling registry...")
      val exitCode = Process(compileCmd).!

      if (exitCode != 0) {
        log.error(s"[smithy4play-codegen] Failed to compile registry (exit code: $exitCode)")
      } else {
        log.info(s"[smithy4play-codegen] Registry compiled successfully")
      }
    }
  }

  override def projectSettings: Seq[Setting[_]] = Seq(
    smithy4playRegistryPackage   := "generated",
    smithy4playRegistryName      := "Smithy4PlayGeneratedRegistry",
    smithy4playRegistryOutputDir := (Compile / sourceManaged).value,

    smithy4playGenerateRegistry := {
      (Compile / compile).value

      val log             = streams.value.log
      val classesDir      = (Compile / classDirectory).value
      val registryPackage = smithy4playRegistryPackage.value
      val registryName    = smithy4playRegistryName.value
      val outputDir       = smithy4playRegistryOutputDir.value
      val fullClasspath   = (Compile / dependencyClasspath).value.map(_.data)
      val scalaInst       = Keys.scalaInstance.value

      generateAndCompileRegistry(
        log, classesDir, registryPackage, registryName, outputDir, fullClasspath, scalaInst
      )

      new File(outputDir, registryPackage.replace('.', '/') + "/" + registryName + ".scala")
    },

    Compile / compile := {
      val analysis        = (Compile / compile).value
      val log             = streams.value.log
      val classesDir      = (Compile / classDirectory).value
      val registryPackage = smithy4playRegistryPackage.value
      val registryName    = smithy4playRegistryName.value
      val outputDir       = smithy4playRegistryOutputDir.value
      val fullClasspath   = (Compile / dependencyClasspath).value.map(_.data)
      val scalaInst       = Keys.scalaInstance.value

      generateAndCompileRegistry(
        log, classesDir, registryPackage, registryName, outputDir, fullClasspath, scalaInst
      )

      analysis
    }
  )
}
