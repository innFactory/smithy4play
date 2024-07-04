import sbt.Compile
import sbt.Keys.cleanFiles

ThisBuild / scalaVersion := Dependencies.scalaVersion
scalaVersion := Dependencies.scalaVersion

val releaseVersion = sys.env.getOrElse("TAG", "2.0.36")
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

// libraryDependencies += Dependencies.smithyOpenapi

val defaultProjectSettings = Seq(
  scalaVersion := Dependencies.scalaVersion,
  scalacOptions ++= Seq("-Ykind-projector:underscores"),
  organization := "de.innfactory",
  version      := releaseVersion
) ++ githubSettings


val sharedSettings   = defaultProjectSettings
lazy val smithy4play = project
  .in(file("smithy4play"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    sharedSettings,
    //addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full),
    scalaVersion                        := Dependencies.scalaVersion,
    Compile / smithy4sExcludedNamespaces := List("de.innfactory.smithy4play.openapi.protocols"),
    Compile / smithy4sInputDirs         := Seq(
      (ThisBuild / baseDirectory).value /"smithy4play"/ "src" / "main" /"resources" / "smithy"
    ),
    Compile / smithy4sOutputDir         := (Compile / sourceManaged).value / "main",
    name                                := "smithy4play",
    libraryDependencies ++= Dependencies.list,
    //autoScalaLibrary := false
  )

lazy val smithy4playTest = project
  .in(file("smithy4playTest"))
  .enablePlugins(Smithy4sCodegenPlugin, PlayScala)
  .settings(
    sharedSettings,
    scalaVersion                        := Dependencies.scalaVersion,
    name                                := "smithy4playTest",

    cleanKeepFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app",
    Compile / smithy4sExcludedNamespaces := List("de.innfactory.smithy4play.openapi.protocols"),
    cleanFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app" / "specs" / "testDefinitions" / "test",
    Compile / smithy4sInputDirs         := Seq((ThisBuild / baseDirectory).value / "smithy4playTest" / "testSpecs"),
    Compile / smithy4sOutputDir         := (ThisBuild / baseDirectory).value / "smithy4playTest" / "app" / "specs",
    Compile / smithy4sAllowedNamespaces := List("aws.protocols", "testDefinitions.test"),
    libraryDependencies ++= Seq(
      guice,
      Dependencies.cats,
      Dependencies.smithyCore,
      Dependencies.smithyInteropCats,
      Dependencies.testTraits % Smithy4s,
      Dependencies.scalatestPlus
    )
  )
  .dependsOn(smithy4play)

lazy val root = project.in(file(".")).settings(sharedSettings).aggregate(smithy4play, smithy4playTest)
