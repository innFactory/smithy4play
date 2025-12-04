package de.innfactory.smithy4play.mcp.server.service.impl

import com.typesafe.config.Config
import de.innfactory.smithy4play.mcp.server.service.ServiceDiscoveryService
import io.github.classgraph.ClassGraph
import smithy4s.Service

import javax.inject.Inject
import scala.util.Try
import scala.jdk.CollectionConverters.*

private[server] class DefaultServiceDiscovery @Inject() (config: Config) extends ServiceDiscoveryService {
  override def discoverServices(): List[Service[?]] =
    try {
      val pkg        = config.getString("smithy4play.autoRoutePackage")
      val scanResult = new ClassGraph()
        .enableClassInfo()
        .acceptPackages(pkg)
        .scan()
      try {
        val serviceClasses = scanResult
          .getClassesImplementing(classOf[smithy4s.Service[?]])
          .asScala
          .toList
        serviceClasses.flatMap { classInfo =>
          Try {
            val clazz           = classInfo.loadClass()
            val moduleField     = clazz.getField("MODULE$")
            val serviceInstance = moduleField.get(null).asInstanceOf[Service[?]]
            serviceInstance
          }.toOption
        }
      } finally {
        scanResult.close()
      }
    } catch {
      case e: Exception =>
        println(s"Error discovering services: ${e.getMessage}")
        e.printStackTrace()
        List.empty
    }
}
