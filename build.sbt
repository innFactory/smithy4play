import sbt.Compile


val releaseVersion = "0.1.30"

val token = sys.env.getOrElse("GITHUB_TOKEN", "")
val githubSettings = Seq(
  githubOwner := "innFactory",
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
  organization := "de.innfactory",
    version := releaseVersion
) ++ githubSettings

val sharedSettings = defaultProjectSettings

lazy val play4s = project
  .in(file("play4s"))
  .settings(
    sharedSettings
  )
  .settings(
    scalaVersion := Dependencies.scalaVersion,
    name := "smithy4play",
    libraryDependencies ++= Dependencies.list
  )


lazy val root = project.in(file(".")).settings(sharedSettings).dependsOn(play4s).aggregate(play4s)