![](docs/smithy4play.png)
![GitHub last commit](https://img.shields.io/github/last-commit/innFactory/smithy4play)
[![Scala Build and Test CI](https://github.com/innFactory/smithy4play/actions/workflows/build.yml/badge.svg)](https://github.com/innFactory/smithy4play/actions/workflows/build.yml)

# Smithy4Play

smithy4play generates Play routes and controllers from your Smithy models using [smithy4s](https://github.com/disneystreaming/smithy4s). The example project `smithy4playTest` shows the current setup with Play 3, Scala 3.3.7, and smithy4s 0.18.46.

## Quick start

1) Add sbt plugins

```scala
// project/plugins.sbt
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.10")
addSbtPlugin("com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "0.18.46")
```

2) Enable plugins and dependencies

```scala
// build.sbt
lazy val myService = project
  .in(file("my-service"))
  .enablePlugins(Smithy4sCodegenPlugin, PlayScala)
  .settings(
    scalaVersion := "3.x.x",
    libraryDependencies ++= Seq(
      "de.innfactory" %% "smithy4play" % "0.5.0",
      "de.innfactory" %% "smithy4play-mcp" % "0.5.0"
    )
  )
```

3) Define your Smithy models (see `smithy4playTest/testSpecs`) and run `sbt compile` to generate service code under `app/specs`.

## Routing

**Auto-routing with MCP (recommended)**

- Configure packages in `conf/application.conf`:
  - `smithy4play.autoRoutePackage = "controller"`
  - `smithy4play.servicePackage = "your.namespace"`
- Bind the router in `conf/routes`:

```text
->      /                       controller.TestRouter
```

- Implement the router by extending `AutoRouterWithMcp` and optionally add middlewares:

```scala
@Singleton
class TestRouter @Inject() (implicit
  mcpController: McpController,
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends AutoRouterWithMcp {

  override def smithy4PlayMiddleware: Seq[Smithy4PlayMiddleware] =
    super.smithy4PlayMiddleware ++ Seq(ValidateAuthMiddleware(), AddHeaderMiddleware())
}
```

**Manual binding**

You can still wire controllers manually by extending `BaseRouter` and returning your controllers as Play `Routes` instances if you do not want auto-routing.

## Implementing controllers

Extend the smithy4s-generated service trait with `ContextRoute` and mix in `Controller` to gain helpers:

```scala
@Singleton
class TestController @Inject() (implicit
  cc: ControllerComponents,
  ec: ExecutionContext,
  ws: WSClient
) extends TestControllerService[ContextRoute]
    with Controller {

  override def test(): ContextRoute[SimpleTestResponse] = Kleisli { _ =>
    EitherT.rightT[Future, Throwable](SimpleTestResponse(Some("ok")))
  }

  override def testWithOutput(
    pathParam: String,
    testQuery: String,
    testHeader: String,
    body: TestRequestBody
  ): ContextRoute[TestWithOutputResponse] = Kleisli { _ =>
    EitherT.rightT[Future, Throwable](TestWithOutputResponse(TestResponseBody(testHeader, pathParam, testQuery, body.message)))
  }
}
```

## Middlewares

Register cross-cutting logic by extending `Smithy4PlayMiddleware` and appending it in your router:

```scala
@Singleton
class ValidateAuthMiddleware @Inject() (implicit ec: ExecutionContext) extends Smithy4PlayMiddleware {
  override def skipMiddleware(r: RoutingContext): Boolean = false

  override def logic(r: RoutingContext, next: RoutingContext => RoutingResult[Result]): RoutingResult[Result] =
    EitherT.leftT[Future, Result](UnauthorizedError("Unauthorized"))
}
```

## MCP endpoints

The MCP module exposes smithy-defined tools. Implement them like any controller:

```scala
@Singleton
class McpTestController @Inject() (implicit cc: ControllerComponents, ec: ExecutionContext, ws: WSClient)
    extends McpControllerService[ContextRoute]
    with Controller {

  def reverseString(text: String, tagged: Option[TaggedTestUnion], untagged: Option[UntaggedTestUnion], discriminated: Option[DiscriminatedTestUnion]): ContextRoute[ReverseStringOutput] = Kleisli { _ =>
    val reversed = text.reverse
    EitherT.rightT[Future, Throwable](ReverseStringOutput(reversed, reversed.replaceAll("\\s", "").length * 2))
  }
}
```

## Configuration reference

Key settings used in `smithy4playTest/conf/application.conf`:

```hocon
smithy4play.autoRoutePackage = "controller"
smithy4play.servicePackage   = "testDefinitions.test"
play.http.parser.maxMemoryBuffer = 100MB
play.filters.enabled = []
```

## Examples

- Full sample project: `smithy4playTest`
- Generated Smithy specs: `smithy4playTest/testSpecs`
- Controllers: `smithy4playTest/app/controller`
- Middlewares: `smithy4playTest/app/controller/middlewares`

For more, browse the sample code and run `sbt test` to execute the compliance tests.
