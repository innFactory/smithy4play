import play.sbt.PlayImport
import sbt.Compile
import sbt.Keys.cleanFiles

ThisBuild / scalaVersion := Dependencies.scalaVersion
scalaVersion             := Dependencies.scalaVersion

val releaseVersion = sys.env.getOrElse("TAG", "3.0.0-LOCAL")
addCommandAlias("packageSmithy4Play", "smithy4play/package")
addCommandAlias(
  "publishSmithy4Play",
  "smithy4play/publish;smithy4playInstrumentation/publish;smithy4playMcp/publish;+ smithy4playBase/publish"
)
addCommandAlias(
  "publishLocalBundle",
  "publishLocalSmithy4PlayInstrumentation;publishLocalSmithy4Play;publishLocalSmithy4PlayBase"
)
addCommandAlias("publishLocalSmithy4PlayInstrumentation", "smithy4playInstrumentation/publishLocal")
addCommandAlias("publishLocalSmithy4PlayBase", "+ smithy4playBase/publishLocal")
addCommandAlias("publishLocalSmithy4Play", "smithy4play/publishLocal")
addCommandAlias("publishLocalSmithy4PlayMcp", "smithy4playMcp/publishLocal")
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
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Nil
      case _            => List("-Ykind-projector:underscores")
    }
  },
  organization := "de.innfactory",
  version      := releaseVersion
) ++ githubSettings

val sharedSettings = defaultProjectSettings

lazy val smithy4playBase = project
  .in(file("smithy4play-base"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    sharedSettings,
    name                                := "smithy4play-base",
    scalaVersion                        := Dependencies.scalaVersion,
    crossScalaVersions                  := Seq(Dependencies.scalaVersion, "2.13.14"),
    Compile / smithy4sInputDirs         := Seq(
      (ThisBuild / baseDirectory).value / "smithy4play-base" / "src" / "main" / "resources" / "smithy"
    ),
    Compile / smithy4sAllowedNamespaces := List("de.innfactory.smithy4play.meta"),
    libraryDependencies ++= Seq(Dependencies.smithyCore)
  )

lazy val smithy4play = project
  .in(file("smithy4play"))
  .dependsOn(smithy4playBase)
  .settings(
    sharedSettings,
    // addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full),
    scalaVersion                := Dependencies.scalaVersion,
    Compile / smithy4sInputDirs := Seq(
      (ThisBuild / baseDirectory).value / "smithy4play" / "src" / "main" / "resources" / "smithy"
    ),
    Compile / smithy4sOutputDir := (Compile / sourceManaged).value / "main",
    name                        := "smithy4play",
    libraryDependencies ++= Dependencies.list
    // autoScalaLibrary := false
  )

lazy val smithy4playInstrumentation = project
  .in(file("smithy4play-instrumentation"))
  .dependsOn(smithy4play)
  .settings(
    sharedSettings,
    scalaVersion                                             := Dependencies.scalaVersion,
    name                                                     := "smithy4play-instrumentation",
    libraryDependencies ++= Dependencies.list,
    libraryDependencies += "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-api"     % "2.5.0",
    libraryDependencies += "io.opentelemetry.javaagent"       % "opentelemetry-javaagent-extension-api" % "2.5.0-alpha"

    // autoScalaLibrary := false
  )

lazy val smithy4playTest = project
  .in(file("smithy4playTest"))
  .enablePlugins(Smithy4sCodegenPlugin, PlayScala)
  .settings(
    sharedSettings,
    scalaVersion                        := Dependencies.scalaVersion,
    name                                := "smithy4playTest",
    cleanKeepFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app",
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
  .dependsOn(smithy4playMcp)

lazy val smithy4playMcp = project
  .in(file("smithy4play-mcp"))
  .dependsOn(smithy4play)
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    sharedSettings,
    scalaVersion                        := Dependencies.scalaVersion,
    name                                := "smithy4play-mcp",
    Compile / smithy4sInputDirs         := Seq(
      (ThisBuild / baseDirectory).value / "smithy4play-mcp" / "src" / "main" / "resources" / "smithy"
    ),
    Compile / smithy4sAllowedNamespaces := List("de.innfactory.smithy4play.mcp"),
    libraryDependencies ++= Seq(
      guice,
      Dependencies.cats,
      Dependencies.smithyCore,
      Dependencies.smithyInteropCats,
      PlayImport.ws,
      PlayImport.specs2 % Test
    )
  )

lazy val root = project.in(file(".")).settings(sharedSettings).aggregate(smithy4play, smithy4playTest, smithy4playMcp)
