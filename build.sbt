import sbt.Compile
import sbt.Keys.cleanFiles

val releaseVersion = sys.env.getOrElse("TAG", "0.2.2-1")

val token          = sys.env.getOrElse("GITHUB_TOKEN", "")
val githubSettings = Seq(
  githubOwner       := "innFactory",
  githubRepository  := "smithy4play",
  githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource
    .Environment("GITHUB_TOKEN"),
  credentials       :=
    Seq(
      Credentials(
        "GitHub Package Registry",
        "maven.pkg.github.com",
        "innFactory",
        token
      )
    )
)

val defaultProjectSettings = Seq(
  scalaVersion := "2.13.8",
  organization := "de.innfactory",
  version      := releaseVersion
) ++ githubSettings

val sharedSettings = defaultProjectSettings

lazy val smithy4play = project
  .in(file("smithy4play"))
  .settings(
    sharedSettings
  )
  .settings(
    scalaVersion    := Dependencies.scalaVersion,
    name            := "smithy4play",
    scalacOptions += "-Ymacro-annotations",
    coverageEnabled := true,
    Compile / compile / wartremoverWarnings ++= Warts.unsafe,
    libraryDependencies ++= Dependencies.list
  )

lazy val smithy4playTest = project
  .enablePlugins(Smithy4sCodegenPlugin, PlayScala)
  .in(file("smithy4playTest"))
  .settings(
    sharedSettings
  )
  .settings(
    scalaVersion                := Dependencies.scalaVersion,
    name                        := "smithy4playTest",
    scalacOptions += "-Ymacro-annotations",
    Compile / compile / wartremoverWarnings ++= Warts.unsafe,
    cleanKeepFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app",
    cleanFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app" / "testDefinitions" / "test",
    Compile / smithy4sInputDir  := (ThisBuild / baseDirectory).value / "smithy4playTest" / "testSpecs",
    Compile / smithy4sOutputDir := (ThisBuild / baseDirectory).value / "smithy4playTest" / "app",
    libraryDependencies += guice,
    libraryDependencies ++= Dependencies.list
  )
  .dependsOn(smithy4play)

lazy val root = project.in(file(".")).settings(sharedSettings).dependsOn(smithy4play).aggregate(smithy4play)

/*
 * smithy4sOutputDir is added automatically to sbt clean
 * -> prevent source code deletion during sbt clean
 */
