![](docs/⇶_smithy4play_⇶.png)
![GitHub last commit](https://img.shields.io/github/last-commit/innFactory/smithy4play)

smithy4play brings the smithy4s http4s route gen to your play server and generates your routes for you
---

**Installation**
---

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

**Usage**
---

- define your controller in smithy files
- generate your scala files (sbt compile)
- create Controller Scala Class
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

**Routing**
---
Autorouting

- Annotate your controller with ```@AutoRouting```
- bind the smithy4play AutoRouter in the play routes file

```scala
-> / de.innfactory.smithy4play.AutoRouter
```

Selfbinding

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

