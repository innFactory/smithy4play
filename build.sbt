import sbt.Compile


val releaseVersion = "0.1.33-1"

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

lazy val smithy4play = project
  .in(file("smithy4play"))
  .settings(
    sharedSettings
  )
  .settings(
    scalaVersion := Dependencies.scalaVersion,
    name := "smithy4play",
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Dependencies.list
  )


lazy val root = project.in(file(".")).settings(sharedSettings).dependsOn(smithy4play).aggregate(smithy4play)