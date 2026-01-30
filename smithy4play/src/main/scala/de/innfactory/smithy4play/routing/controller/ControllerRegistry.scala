package de.innfactory.smithy4play.routing.controller

import de.innfactory.smithy4play.logger
import io.github.classgraph.{ ClassGraph, ScanResult }

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * Registry for auto-routable controllers with caching support.
 * 
 * This object caches classpath scan results to avoid expensive re-scanning
 * on every router instantiation. The cache is keyed by package name.
 */
object ControllerRegistry {

  private val cache = new ConcurrentHashMap[String, Seq[Class[?]]]()
  
  /**
   * Clear the controller cache. Useful for testing or hot-reload scenarios.
   */
  def clearCache(): Unit = {
    cache.clear()
    logger.debug("[ControllerRegistry] Cache cleared")
  }

  /**
   * Get cached controller classes for the given package, or scan if not cached.
   * 
   * @param pkg The package to scan for controllers implementing [[AutoRoutableController]]
   * @return Sequence of controller classes found in the package
   */
  def getControllers(pkg: String): Seq[Class[?]] = {
    cache.computeIfAbsent(pkg, scanPackage)
  }

  /**
   * Explicitly register controller classes for a package.
   * This bypasses classpath scanning entirely.
   * 
   * @param pkg The package identifier (used as cache key)
   * @param controllers The controller classes to register
   */
  def registerControllers(pkg: String, controllers: Seq[Class[?]]): Unit = {
    cache.put(pkg, controllers)
    logger.debug(s"[ControllerRegistry] Registered ${controllers.size} controllers for package: $pkg")
  }

  /**
   * Check if controllers are already cached for the given package.
   */
  def isCached(pkg: String): Boolean = {
    cache.containsKey(pkg)
  }

  private def scanPackage(pkg: String): Seq[Class[?]] = {
    logger.debug(s"[ControllerRegistry] Scanning package: $pkg")
    val startTime = System.nanoTime()
    
    val classGraphScanner: ScanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(pkg)
      .scan()
    
    try {
      val controllerInfos = classGraphScanner.getClassesImplementing(classOf[AutoRoutableController])
      val controllers = controllerInfos.asScala
        .filter(!_.isAbstract)
        .map(_.loadClass(true))
        .toSeq
      
      val elapsed = (System.nanoTime() - startTime) / 1_000_000.0
      logger.debug(s"[ControllerRegistry] Found ${controllers.size} controllers in ${elapsed}ms")
      
      controllers
    } finally {
      classGraphScanner.close()
    }
  }
}
