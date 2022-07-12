ThisBuild / organization := "org.innFactory"
ThisBuild / scalaVersion := "2.13.8"

/*lazy val root = (project in file("."))
  .enablePlugins(Smithy4sCodegenPlugin, PlayScala)
  .settings(
    smithy4sInputDir in Compile := (baseDirectory in ThisBuild).value / "smithy-in",
    smithy4sOutputDir in Compile := (baseDirectory in ThisBuild).value / "smithy_output",
    name := "smithy4s-play",
    scalaVersion := Dependencies.scalaVersion,
    libraryDependencies ++= Dependencies.list,
  )*/

lazy val play4s = project
  .in(file("modules/play4s"))
  .settings(
    scalaVersion := Dependencies.scalaVersion,
    version := "0.0.8",
    name := "smithy4s-play",
    libraryDependencies ++= Dependencies.list
  )

