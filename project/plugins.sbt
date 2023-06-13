addSbtPlugin("com.codecommit"               %% "sbt-github-packages"  % "0.5.3")
addSbtPlugin("org.wartremover"              %% "sbt-wartremover"      % "3.1.3")
addSbtPlugin("org.scalameta"                %% "sbt-scalafmt"         % "2.5.0")
addSbtPlugin("com.disneystreaming.smithy4s" %% "smithy4s-sbt-codegen" % "0.17.8")
addSbtPlugin("com.typesafe.play"            %% "sbt-plugin"           % "2.8.19")
addSbtPlugin("org.scoverage"                %% "sbt-scoverage"        % "2.0.8")

ThisBuild / dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)
