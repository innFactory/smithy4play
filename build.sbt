import sbt.Compile
import sbt.Keys.cleanFiles
val releaseVersion = sys.env.getOrElse("TAG", "1.0.0-Beta2")
addCommandAlias("publishSmithy4Play", "smithy4play/publish")
addCommandAlias("publishLocalSmithy4Play", "smithy4play/publishLocal")
addCommandAlias("generateCoverage", "clean; coverage; test; coverageReport")
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

scalaVersion := "3.3.1"

val defaultProjectSettings = Seq(
  scalaVersion := "3.3.1",
  organization := "de.innfactory",
  version      := releaseVersion
) ++ githubSettings

val sharedSettings = defaultProjectSettings

lazy val smithy4play = project
  .in(file("smithy4play"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    sharedSettings,
    scalaVersion                        := Dependencies.scalaVersion,
    Compile / smithy4sAllowedNamespaces := List("smithy.smithy4play"),
    Compile / smithy4sInputDirs         := Seq(
      (ThisBuild / baseDirectory).value / "smithy4play" / "src" / "resources" / "META_INF" / "smithy"
    ),
    Compile / smithy4sOutputDir         := (ThisBuild / baseDirectory).value / "smithy4play" / "target" / "scala-3.3.1" / "src_managed" / "main",
    name                                := "smithy4play",
    Compile / compile / wartremoverWarnings ++= Warts.unsafe,
    libraryDependencies ++= Dependencies.list
  )

lazy val smithy4playTest = project
  .in(file("smithy4playTest"))
  .enablePlugins(Smithy4sCodegenPlugin, PlayScala)
  .settings(
    sharedSettings,
    scalaVersion                := Dependencies.scalaVersion,
    name                        := "smithy4playTest",
    Compile / compile / wartremoverWarnings ++= Warts.unsafe,
    cleanKeepFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app",
    cleanFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app" / "testDefinitions" / "test",
    Compile / smithy4sInputDirs := Seq((ThisBuild / baseDirectory).value / "smithy4playTest" / "testSpecs"),
    Compile / smithy4sOutputDir := (ThisBuild / baseDirectory).value / "smithy4playTest" / "app",
    libraryDependencies ++= Seq(
      guice,
      Dependencies.cats,
      Dependencies.smithyCore,
      Dependencies.testTraits % Smithy4s,
      Dependencies.scalatestPlus
    )
  )
  .dependsOn(smithy4play)

lazy val root = project.in(file(".")).settings(sharedSettings).aggregate(smithy4play, smithy4playTest)
