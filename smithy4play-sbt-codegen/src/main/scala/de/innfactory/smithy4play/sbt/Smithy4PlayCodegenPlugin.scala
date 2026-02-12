package de.innfactory.smithy4play.sbt

import sbt._
import sbt.Keys._
import scala.io.Source
import scala.util.Using
import scala.sys.process._

object Smithy4PlayCodegenPlugin extends AutoPlugin {

  object autoImport {
    val smithy4playRegistryPackage   = settingKey[String]("Package for the generated registry")
    val smithy4playRegistryName      = settingKey[String]("Name of the generated registry object")
    val smithy4playRegistryOutputDir = settingKey[File]("Output directory for the generated registry")
  }

  import autoImport._

  override def trigger = noTrigger

  override def requires = plugins.JvmPlugin

  private def readExistingRegistry(registrySourceFile: File): Option[String] =
    if (registrySourceFile.exists()) {
      Using(Source.fromFile(registrySourceFile))(_.mkString).toOption
    } else {
      None
    }

  private def compileRegistry(
    log: sbt.util.Logger,
    registrySourceFile: File,
    classesDir: File,
    fullClasspath: Seq[File],
    scalaInst: xsbti.compile.ScalaInstance
  ): Boolean = {
    val classpathStr = (classesDir +: fullClasspath).map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

    val compileCmd = Seq(
      "java",
      "-cp",
      scalaInst.allJars.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator),
      "dotty.tools.dotc.Main",
      "-classpath",
      classpathStr,
      "-d",
      classesDir.getAbsolutePath,
      registrySourceFile.getAbsolutePath
    )

    log.info(s"[smithy4play-codegen] Compiling registry...")
    val exitCode = Process(compileCmd).!

    if (exitCode != 0) {
      log.error(s"[smithy4play-codegen] Failed to compile registry (exit code: $exitCode)")
      false
    } else {
      log.info(s"[smithy4play-codegen] Registry compiled successfully")
      true
    }
  }

  private def generateAndCompileRegistry(
    log: sbt.util.Logger,
    classesDir: File,
    registryPackage: String,
    registryName: String,
    outputDir: File,
    fullClasspath: Seq[File],
    scalaInst: xsbti.compile.ScalaInstance
  ): Unit = {
    val registrySourceFile = new File(
      outputDir,
      registryPackage.replace('.', '/') + "/" + registryName + ".scala"
    )

    val allClasspathEntries = classesDir +: fullClasspath

    val services    = ServiceScanner.scan(allClasspathEntries)
    val controllers = ControllerScanner.scan(allClasspathEntries)

    // Generate the new content to compare
    val newContent = RegistryGenerator.generateContent(
      services = services,
      controllers = controllers,
      registryPackage = registryPackage,
      registryName = registryName
    )

    val existingContent   = readExistingRegistry(registrySourceFile)
    val needsRegeneration = !existingContent.contains(newContent)

    if (needsRegeneration) {
      val reason = if (existingContent.isEmpty) "Registry not found" else "Services or controllers changed"
      log.info(s"[smithy4play-codegen] $reason, generating registry...")

      log.info(s"[smithy4play-codegen] Found ${services.size} services")
      services.foreach(s => log.info(s"[smithy4play-codegen]   - ${s.fullyQualifiedName}"))

      log.info(s"[smithy4play-codegen] Found ${controllers.size} controllers")
      controllers.foreach(c => log.info(s"[smithy4play-codegen]   - ${c.fullyQualifiedName}"))

      val matched = ControllerScanner.matchControllersToServices(controllers, services)
      matched.foreach { case (controller, serviceOpt) =>
        serviceOpt match {
          case Some(service) =>
            log.info(s"[smithy4play-codegen] Matched ${controller.className} -> ${service.objectName}")
          case None          =>
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

      // Compile the registry immediately so it's available for tests
      compileRegistry(log, outputFile, classesDir, fullClasspath, scalaInst)
    }
  }

  override def projectSettings: Seq[Setting[_]] = Seq(
    smithy4playRegistryPackage   := "generated",
    smithy4playRegistryName      := "Smithy4PlayGeneratedRegistry",
    smithy4playRegistryOutputDir := (Compile / sourceManaged).value,

    // After compilation, generate and compile the registry
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
        log,
        classesDir,
        registryPackage,
        registryName,
        outputDir,
        fullClasspath,
        scalaInst
      )

      analysis
    }
  )
}
