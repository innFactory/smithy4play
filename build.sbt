import sbt.Compile

val releaseVersion = "0.1.97"
name := "smithy4play"

val token = sys.env.getOrElse("GITHUB_TOKEN", "")
val githubSettings = Seq(
  githubOwner := "innFactory",
  githubRepository := "de.innfactory.smithy4play",
  githubRepository := "smithy4play",
  githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource
    .Environment("GITHUB_TOKEN"),
  credentials :=
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
  organization := "de.innfactory.smithy4play",
  version := releaseVersion,
  githubOwner := "innFactory"
) ++ githubSettings

val sharedSettings = defaultProjectSettings

lazy val play4s = project
  .enablePlugins(Smithy4sCodegenPlugin)
  .in(file("play4s"))
  .settings(
    sharedSettings,
    Compile / smithy4sInputDir := (ThisBuild / baseDirectory).value / "play4s" / "resources",
    Compile / smithy4sOutputDir := (ThisBuild / baseDirectory).value / "play4s" / "src" / "main" / "scala" / "de" / "innfactory" / "smithy4play"
  )
  .settings(
    scalaVersion := Dependencies.scalaVersion,
    name := "play4s",
    libraryDependencies ++= Dependencies.list
  )
