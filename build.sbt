val releaseVersion = "0.1.9"
name:= "smithy4s-play4s"

val token = sys.env.getOrElse("GITHUB_TOKEN", "")
val githubSettings = Seq(
  githubOwner := "innFactory",
  githubRepository := "de.innfactory.play-endpoint-builder",
  githubRepository := "play-endpoint-builder",
  githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource.Environment("GITHUB_TOKEN"),
  credentials :=
    Seq(Credentials(
      "GitHub Package Registry",
      "maven.pkg.github.com",
      "innFactory",
      token
    ))
)
val defaultProjectSettings = Seq(
  scalaVersion := "2.13.8",
  organization := "de.innfactory.play-endpoint-builder",
  version := releaseVersion,
  githubOwner := "innFactory",
) ++ githubSettings

val sharedSettings = defaultProjectSettings

lazy val play4s = project
  .in(file("play4s"))
  .settings(
    sharedSettings
  )
  .settings(
    scalaVersion := Dependencies.scalaVersion,
    name := "play4s",
    libraryDependencies ++= Dependencies.list,
  )

lazy val root = project.in(file(".")).settings(sharedSettings).dependsOn(play4s).aggregate(play4s)

