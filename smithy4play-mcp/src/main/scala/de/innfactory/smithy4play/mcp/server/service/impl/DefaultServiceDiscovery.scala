package de.innfactory.smithy4play.mcp.server.service.impl

import com.typesafe.config.Config
import de.innfactory.smithy4play.mcp.server.service.ServiceDiscoveryService
import io.github.classgraph.ClassGraph
import play.api.Logger
import smithy4s.Service

import javax.inject.Inject
import scala.util.Try
import scala.jdk.CollectionConverters.*

private[server] class DefaultServiceDiscovery @Inject() (config: Config) extends ServiceDiscoveryService {
  private val logger = Logger(this.getClass)

  override def discoverServices(): List[Service[?]] =
    try {
      val pkg        = config.getString("smithy4play.servicePackage")
      val scanResult = new ClassGraph()
        .enableClassInfo()
        .acceptPackages(pkg)
        .scan()
      try {
        val serviceClasses = scanResult
          .getClassesImplementing(classOf[smithy4s.Service[?]])
          .asScala
          .toList

        logger.info(s"Discovered service classes: ${serviceClasses.map(_.getName).mkString(", ")}")
        serviceClasses.flatMap { classInfo =>
          Try {
            val clazz           = classInfo.loadClass()
            val moduleField     = clazz.getField("MODULE$")
            val serviceInstance = moduleField.get(null).asInstanceOf[Service[?]]
            serviceInstance
          }.toOption
        }
      } finally scanResult.close()
    } catch {
      case e: Exception =>
        logger.error("Error discovering services", e)
        List.empty
    }
}
