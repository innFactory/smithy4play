sbtPlugin := true

name := "smithy4play-sbt-codegen"

organization := "de.innfactory"

version := sys.env.getOrElse("TAG", "1.1.2")

scalaVersion := "2.12.20"

libraryDependencies ++= Seq(
  "io.github.classgraph" % "classgraph" % "4.8.179"
)

val token = sys.env.getOrElse("GITHUB_TOKEN", "")

githubOwner := "innFactory"
githubRepository := "smithy4play"
githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource.Environment("GITHUB_TOKEN")
credentials := Seq(
  Credentials(
    "GitHub Package Registry",
    "maven.pkg.github.com",
    "innFactory",
    token
  )
)
