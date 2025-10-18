![](docs/smithy4play.png)
![GitHub last commit](https://img.shields.io/github/last-commit/innFactory/smithy4play)
[![Scala Build and Test CI](https://github.com/innFactory/smithy4play/actions/workflows/build.yml/badge.svg)](https://github.com/innFactory/smithy4play/actions/workflows/build.yml)

# Smithy4Play

smithy4play is a routing gernator for the play2 framework based on [smithy4s](https://github.com/disneystreaming/smithy4s). Just write your smithy API definition and the plugin will generate everything you need for play2 framework usage.

---

## QuickStart 
### Add smithy4play to your project 

plugins.sbt

```scala
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.*.*")
addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.*.*")
addSbtPlugin(
  "com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "0.*.*"
)
```

build.sbt

```scala
.enablePlugins(Smithy4sCodegenPlugin, PlayScala)
  .settings(
    githubSettings,
    Compile / smithy4sInputDir := (ThisBuild / baseDirectory).value / "smithy-in",
    Compile / smithy4sOutputDir := (ThisBuild / baseDirectory).value / "smithy-out",
    libraryDependencies += "de.innfactory" %% "smithy4play" % "latestVersion")
```

### Usage

- define your controllers in smithy files
- use smithy4s codegen (sbt compile)

**Server**
- create controller scala class
- extend your Controller with the generated Scala Type and smithy4play Type ContextRoute

```scala
@Singleton
class PreviewController @Inject(
)(implicit
  cc: ControllerComponents,
  ec: ExecutionContext
 ) extends PreviewControllerService[ContextRoute] {

  override def preview(bye: PreviewBody): ContextRoute[PreviewResponse] =
    Kleisli { rc =>
      EitherT.rightT[Future, ContextRouteError](PreviewResponse(Some("Hello")))
    }
}
```

**Client**
- import the EnhancedGenericAPIClient
```scala
import de.innfactory.smithy4play.client.GenericAPIClient.EnhancedGenericAPIClient
val previewControllerClient = PreviewControllerServiceGen.withClient(FakeRequestClient)
previewControllerClient.preview()
```
For a further examples take a look at the smithy4playTest project.

## Routing

You can choose between autorouting or selfbinding.

### Autorouting

- Annotate your controller with ```@AutoRouting```
- add ```scalacOptions += "-Ymacro-annotations"``` to your build.sbt settings to enable macro annotations
- bind the smithy4play AutoRouter in the play routes file

```scala
-> / de.innfactory.smithy4play.AutoRouter
```
- add package name to configuration
```scala
smithy4play.autoRoutePackage = "your.package.name"
```

### Selfbinding

- Create a ApiRouter class and inject your controller

```scala
@Singleton
class ApiRouter @Inject()(
  homeController: HomeController,
  pizzaController: PizzaController
)(implicit
  cc: ControllerComponents,
  ec: ExecutionContext
) extends BaseRouter {

  override val controllers: Seq[Routes] =
    Seq(homeController, pizzaController)
}

```

- bind the ApiRouter in the play routes file

```scala
-> / api.ApiRouter
```

## Middlewares

If you want Middlewares that run before the endpoint logic follow these steps:

- Implement Middlewares
```scala
@Singleton
class ExampleMiddleware @Inject() (implicit
  executionContext: ExecutionContext
) extends MiddlewareBase {

  override protected def skipMiddleware(r: RoutingContext): Boolean = false

  override def logic(
    r: RoutingContext,
    next: RoutingContext => RouteResult[EndpointResult]
  ): RouteResult[EndpointResult] =
    next(r)
}
```
- Implement a MiddlewareRegistry and register your middlewares
```scala
class MiddlewareRegistry @Inject() (
  disableAbleMiddleware: DisableAbleMiddleware,
  testMiddlewareImpl: TestMiddlewareImpl,
  validateAuthMiddleware: ValidateAuthMiddleware
) extends MiddlewareRegistryBase {
  override val middlewares: Seq[MiddlewareBase] = Seq(ExampleMiddleware)
}
```

## MCP (Model Context Protocol) Support

smithy4play now includes built-in support for the Model Context Protocol, enabling LLMs to interact with your Smithy APIs as tools.

### Quick Start

1. Annotate your operations with MCP traits:

```smithy
use de.innfactory.mcp#mcpTool
use de.innfactory.mcp#mcpName
use de.innfactory.mcp#mcpDescription

@mcpTool
@mcpName("get_user")
@mcpDescription("Retrieves a user by ID")
@http(method: "GET", uri: "/user/{id}")
operation GetUser { ... }
```

2. MCP endpoints are automatically available:
   - `GET /mcp/tools` - Lists available tools
   - `POST /mcp/call` - Executes tools

For detailed documentation, see [MCP.md](docs/MCP.md).

## Credits:

[innFactory ❤️ Open Source](https://innfactory.de)