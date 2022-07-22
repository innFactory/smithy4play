val releaseVersion = "0.1.14"
name:= "smithy4play"

val token = sys.env.getOrElse("GITHUB_TOKEN", "")
val githubSettings = Seq(
  githubOwner := "innFactory",
  githubRepository := "de.innfactory.smithy4play",
  githubRepository := "smithy4play",
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
  organization := "de.innfactory.smithy4play",
  version := releaseVersion,
  githubOwner := "innFactory",
) ++ githubSettings

val sharedSettings = defaultProjectSettings

lazy val play4s = project
  .in(file("smithy4play"))
  .settings(
    sharedSettings

  )
  .settings(
    scalaVersion := Dependencies.scalaVersion,
    name := "smithy4play",
    libraryDependencies ++= Dependencies.list,
  )

lazy val root = project.in(file(".")).settings(sharedSettings).dependsOn(play4s).aggregate(play4s)

