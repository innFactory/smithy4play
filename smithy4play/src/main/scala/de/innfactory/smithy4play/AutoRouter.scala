package de.innfactory.smithy4play

import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.typesafe.config.Config
import de.innfactory.smithy4play.mcp.{MCPRouter, MCPToolDiscovery}
import de.innfactory.smithy4play.middleware.{ MiddlewareBase, MiddlewareRegistryBase, ValidateAuthMiddleware }
import io.github.classgraph.{ ClassGraph, ScanResult }
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes


import java.util.Optional
import javax.inject.{ Inject, Provider, Singleton }
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

@Singleton
class AutoRouter @Inject(
) (validateAuthMiddleware: ValidateAuthMiddleware,
   mcpRouter: MCPRouter)(implicit
  cc: ControllerComponents,
  app: Application,
  ec: ExecutionContext,
  config: Config
) extends BaseRouter {

  private val pkg          = config.getString("smithy4play.autoRoutePackage")
  private val readerConfig = ReaderConfig.fromApplicationConfig(config)

  override val controllers: Seq[Routes] = {
    val classGraphScanner: ScanResult = new ClassGraph().enableAllInfo().acceptPackages(pkg).scan()
    val controllers                   = classGraphScanner.getClassesImplementing(classOf[AutoRoutableController])
    val middlewares                   = Try {
      app.injector.instanceOf[MiddlewareRegistryBase].middlewares
    }.toOption.getOrElse(Seq(validateAuthMiddleware))
    logger.debug(s"[AutoRouter] found ${controllers.size().toString} controllers")
    logger.debug(s"[AutoRouter] found ${middlewares.size.toString} middlewares")
    val routes                        = controllers.asScala.map(_.loadClass(true)).map(clazz => createFromClass(clazz, middlewares)).toSeq
    classGraphScanner.close()
    
    // Add MCP routes to the list of routes
    routes :+ mcpRouter.routes
  }

  private def createFromClass(clazz: Class[_], middlewares: Seq[MiddlewareBase]): Routes =
    app.injector.instanceOf(clazz) match {
      case c: AutoRoutableController => 
        // Register the service with MCP if it has MCP-annotated operations
        registerServiceWithMCP(c)
        c.router(middlewares, readerConfig)
    }

  /**
   * Register a controller's service with the MCP router for tool discovery
   */
  private def registerServiceWithMCP(controller: AutoRoutableController): Unit = {
    // This is a simplified approach - in practice, we'd need to extract the Service
    // from the controller. For now, we'll leave this as a placeholder.
    logger.debug(s"[AutoRouter] Checking controller ${controller.getClass.getSimpleName} for MCP annotations")
  }

}
