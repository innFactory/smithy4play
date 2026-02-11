import play.sbt.PlayImport
import sbt.Compile
import sbt.Keys.cleanFiles

ThisBuild / scalaVersion := Dependencies.scalaVersion
scalaVersion             := Dependencies.scalaVersion

val releaseVersion = sys.env.getOrElse("TAG", "1.1.2")
addCommandAlias("packageSmithy4Play", "smithy4play/package")
addCommandAlias(
  "publishSmithy4Play",
  "smithy4play/publish;smithy4playInstrumentation/publish;smithy4playMcp/publish;+ smithy4playBase/publish;smithy4playSbtCodegen/publish"
)
addCommandAlias(
  "publishLocalBundle",
  "publishLocalSmithy4PlayInstrumentation;publishLocalSmithy4Play;publishLocalSmithy4PlayBase;publishLocalSmithy4PlayMcp;publishLocalSmithy4PlaySbtCodegen"
)
addCommandAlias("publishLocalSmithy4PlayInstrumentation", "smithy4playInstrumentation/publishLocal")
addCommandAlias("publishLocalSmithy4PlayBase", "+ smithy4playBase/publishLocal")
addCommandAlias("publishLocalSmithy4Play", "smithy4play/publishLocal")
addCommandAlias("publishLocalSmithy4PlayMcp", "smithy4playMcp/publishLocal")
addCommandAlias("publishLocalSmithy4PlaySbtCodegen", "smithy4playSbtCodegen/publishLocal")
addCommandAlias("generateCoverage", "clean; coverage; test; coverageReport")

// Test / benchmark / performance aliases
addCommandAlias("testAll", "test")
addCommandAlias("testNormal", "test")
addCommandAlias("testBench", "smithy4playBenchmarks/Jmh/run")
addCommandAlias("testBenchQuick", "smithy4playBenchmarks/Jmh/run -f 1 -wi 1 -i 1 -r 200ms -w 200ms")
addCommandAlias("testGatling", "smithy4playGatling / Gatling / test")

// Benchmark aliases
addCommandAlias("runBenchmarks", "smithy4playBenchmarks/Jmh/run")
addCommandAlias("runPathBenchmarks", "smithy4playBenchmarks/Jmh/run PathParsingBenchmarks")
addCommandAlias("runCodecBenchmarks", "smithy4playBenchmarks/Jmh/run CodecResolutionBenchmarks")
addCommandAlias("runBlobBenchmarks", "smithy4playBenchmarks/Jmh/run LazyBlobBenchmarks")

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
  version      := releaseVersion,
  dependencyOverrides ++= Seq(
    "software.amazon.smithy" % "smithy-utils" % Dependencies.smithyVersion
  )
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
  .enablePlugins(Smithy4sCodegenPlugin, PlayScala, Smithy4PlayCodegenPlugin)
  .settings(
    sharedSettings,
    scalaVersion                        := Dependencies.scalaVersion,
    name                                := "smithy4playTest",
    cleanKeepFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app",
    cleanFiles += (ThisBuild / baseDirectory).value / "smithy4playTest" / "app" / "specs" / "testDefinitions" / "test",
    Compile / smithy4sInputDirs         := Seq((ThisBuild / baseDirectory).value / "smithy4playTest" / "testSpecs"),
    Compile / smithy4sOutputDir         := (ThisBuild / baseDirectory).value / "smithy4playTest" / "app" / "specs",
    Compile / smithy4sAllowedNamespaces := List("aws.protocols", "testDefinitions.test", "smithy.test"),
    smithy4playRegistryPackage          := "controller",
    smithy4playRegistryName             := "Smithy4PlayGeneratedRegistry",
    Test / fork                         := true,
    Test / javaOptions ++= Seq(
      "-Dgatling.core.directory.results=target/gatling-smithy",
      "-Dgatling.core.directory.binaries=target/gatling-binaries-smithy"
    ),
    dependencyOverrides ++= Seq(
      // Play/Pekko + jackson-module-scala 2.14.x expects Jackson < 2.15
      "com.fasterxml.jackson.core" % "jackson-databind"    % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-core"        % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-annotations" % "2.14.3"
    ),
    libraryDependencies ++= Seq(
      guice,
      Dependencies.cats,
      Dependencies.smithyCore,
      Dependencies.smithyInteropCats,
      Dependencies.scalatestPlus,
      "software.amazon.smithy"  % "smithy-protocol-test-traits" % Dependencies.smithyVersion,
      ("io.gatling.highcharts"  % "gatling-charts-highcharts"   % "3.14.9" % Test)
        .exclude("org.scala-lang.modules", "scala-parser-combinators_2.13"),
      ("io.gatling"             % "gatling-test-framework"      % "3.14.9" % Test)
        .exclude("org.scala-lang.modules", "scala-parser-combinators_2.13"),
      "org.scala-lang.modules" %% "scala-parser-combinators"    % "2.4.0"  % Test
    )
  )
  .dependsOn(smithy4playMcp)

lazy val smithy4playMcp = project
  .in(file("smithy4play-mcp"))
  .dependsOn(smithy4play, smithy4playBase)
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    sharedSettings,
    scalaVersion                        := Dependencies.scalaVersion,
    name                                := "smithy4play-mcp",
    Compile / smithy4sInputDirs         := Seq(
      (ThisBuild / baseDirectory).value / "smithy4play-mcp" / "src" / "main" / "resources" / "smithy"
    ),
    Compile / smithy4sAllowedNamespaces := List("de.innfactory.smithy4play.mcp"),
    dependencyOverrides ++= Seq(
      // Play/Pekko + jackson-module-scala 2.14.x expects Jackson < 2.15
      "com.fasterxml.jackson.core" % "jackson-databind"    % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-core"        % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-annotations" % "2.14.3"
    ),
    libraryDependencies ++= Seq(
      guice,
      Dependencies.cats,
      Dependencies.smithyCore,
      Dependencies.smithyInteropCats,
      PlayImport.ws,
      PlayImport.specs2 % Test
    )
  )

lazy val smithy4playBenchmarks = project
  .in(file("smithy4play-benchmarks"))
  .dependsOn(smithy4play)
  .enablePlugins(JmhPlugin)
  .settings(
    sharedSettings,
    scalaVersion   := Dependencies.scalaVersion,
    name           := "smithy4play-benchmarks",
    publish / skip := true,
    libraryDependencies ++= Seq(
      Dependencies.typesafePlay,
      Dependencies.cats
    )
  )

lazy val smithy4playGatlingWrapped      = taskKey[sbt.Tests.Output]("Start test server, run Gatling, then stop")
lazy val smithy4playGatlingServerHandle =
  taskKey[(sbt.BackgroundJobService, sbt.JobHandle)]("Background Play server handle for Gatling")

lazy val smithy4playGatling = project
  .in(file("smithy4play-gatling"))
  .enablePlugins(GatlingPlugin)
  .dependsOn(smithy4playTest)
  .settings(
    sharedSettings,
    scalaVersion                   := Dependencies.scalaVersion,
    name                           := "smithy4play-gatling",
    publish / skip                 := true,
    libraryDependencies ++= Seq(
      ("io.gatling.highcharts"     % "gatling-charts-highcharts" % "3.14.9" % Test)
        .exclude("org.scala-lang.modules", "scala-parser-combinators_2.13"),
      ("io.gatling"                % "gatling-test-framework"    % "3.14.9" % Test)
        .exclude("org.scala-lang.modules", "scala-parser-combinators_2.13"),
      "org.scala-lang.modules"    %% "scala-parser-combinators"  % "2.4.0"  % Test,
      "com.fasterxml.jackson.core" % "jackson-core"              % "2.18.3" % Test,
      "com.fasterxml.jackson.core" % "jackson-databind"          % "2.18.3" % Test,
      "com.fasterxml.jackson.core" % "jackson-annotations"       % "2.18.3" % Test
    ),
    // Don't run Gatling simulations as part of the normal `test` task.
    // Use `smithy4playGatling / Gatling / test` when you want to run them.
    testFrameworks                 := testFrameworks.value.filterNot(_.implClassNames.contains("io.gatling.sbt.GatlingFramework")),
    Test / fork                    := true,
    Test / javaOptions ++= Seq(
      "-Dgatling.core.directory.results=target/gatling-smithy",
      "-Dgatling.core.directory.binaries=target/gatling-binaries-smithy",
      "-Dgatling.resultsFolder=target/gatling-smithy"
    ),
    Gatling / fork                 := true,
    Gatling / javaOptions ++= Seq(
      "-Xmx1G",
      "-DbaseUrl=http://127.0.0.1:9000",
      "-Dgatling.core.directory.results=target/gatling-smithy",
      "-Dgatling.core.directory.binaries=target/gatling-binaries-smithy",
      "-Dgatling.resultsFolder=target/gatling-smithy"
    ),
    smithy4playGatlingServerHandle := {
      val log     = streams.value.log
      log.info("[smithy4play-gatling] Starting Play server via smithy4playTest/bgRun")
      val service = (Global / bgJobService).value
      val handle  = (smithy4playTest / Compile / bgRun).toTask("").value
      (service, handle)
    },
    smithy4playGatlingWrapped      := {
      val log  = streams.value.log
      val host = "127.0.0.1"
      val port = 9000

      def waitForEndpointReady(maxWaitMs: Int): Unit = {
        val deadline = System.currentTimeMillis() + maxWaitMs
        var ok       = false

        while (!ok && System.currentTimeMillis() < deadline)
          try {
            val conn = new java.net.URI(s"http://$host:$port/test/thisIsAPathParam?testQuery=thisIsATestQuery").toURL
              .openConnection()
              .asInstanceOf[java.net.HttpURLConnection]
            conn.setConnectTimeout(500)
            conn.setReadTimeout(500)
            conn.setRequestMethod("POST")
            conn.setDoOutput(true)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Test-Header", "thisIsATestHeader")
            val body = """{"message":"thisIsARequestBody"}""".getBytes("UTF-8")
            conn.setFixedLengthStreamingMode(body.length)
            val os   = conn.getOutputStream
            os.write(body)
            os.close()

            ok = conn.getResponseCode == 200
            conn.disconnect()
          } catch {
            case _: Throwable =>
              Thread.sleep(250)
          }

        if (!ok) sys.error(s"Play test endpoint never became ready on $host:$port")
      }

      val (service, handle) = smithy4playGatlingServerHandle.value
      try {
        waitForEndpointReady(30000)
        log.info("[smithy4play-gatling] Endpoint ready; starting Gatling")
        (Gatling / executeTests).value
      } finally {
        log.info("[smithy4play-gatling] Stopping Play server")
        service.stop(handle)
      }
    },
    Gatling / test                 := smithy4playGatlingWrapped.value
  )

lazy val smithy4playSbtCodegen = project
  .in(file("smithy4play-sbt-codegen"))
  .settings(
    sbtPlugin         := true,
    name              := "smithy4play-sbt-codegen",
    organization      := "de.innfactory",
    version           := releaseVersion,
    scalaVersion      := "2.12.20",
    libraryDependencies ++= Seq(
      "io.github.classgraph" % "classgraph" % "4.8.179"
    ),
    githubOwner       := "innFactory",
    githubRepository  := "smithy4play",
    githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource.Environment("GITHUB_TOKEN"),
    credentials       := Seq(
      Credentials(
        "GitHub Package Registry",
        "maven.pkg.github.com",
        "innFactory",
        token
      )
    )
  )

lazy val root = project
  .in(file("."))
  .settings(sharedSettings)
  .aggregate(
    smithy4play,
    smithy4playTest,
    smithy4playMcp,
    smithy4playBenchmarks,
    smithy4playGatling,
    smithy4playSbtCodegen
  )
