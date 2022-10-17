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
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.15")
addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.3")
addSbtPlugin(
  "com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "0.14.2"
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
- create Client Class
- extend the Client with the generated Scala Type and smithy4play Type ClientResponse
- implement a smithy4play RequestClient that handles the request

```scala
class PreviewControllerClient(
  additionalHeaders: Map[String, Seq[String]] = Map.empty, 
  baseUri: String = "/")
  (implicit ec: ExecutionContext, client: RequestClient)
  extends PreviewControllerService[ClientResponse] {

  val smithyPlayClient = new SmithyPlayClient(baseUri, TestControllerService.service)

  override def preview(): ClientResponse[SimpleTestResponse] =
    smithyPlayClient.send(PreviewControllerServiceGen.Preview(), Some(additionalHeaders))
}
```
now the methods from the client can be accessed like this:
```scala
val previewControllerClient = new PreviewControllerClient()
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

## Credits:

[innFactory ❤️ Open Source](https://innfactory.de)