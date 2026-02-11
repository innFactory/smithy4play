package de.innfactory.smithy4play.benchmarks

import de.innfactory.smithy4play.routing.controller.ControllerRegistry
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit

/** Benchmarks for controller registry operations.
  *
  * These benchmarks measure the caching effectiveness of the ControllerRegistry.
  *
  * Run with: sbt "smithy4playBenchmarks/Jmh/run ControllerRegistryBenchmarks"
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ControllerRegistryBenchmarks {

  val testPackage        = "de.innfactory.smithy4play.benchmarks.fixtures"
  val nonExistentPackage = "nonexistent.package.that.does.not.exist"

  @Setup(Level.Iteration)
  def setup(): Unit =
    // Warm up the cache with the test package
    ControllerRegistry.getControllers(testPackage)

  @Benchmark
  def getCachedControllers(bh: Blackhole): Unit =
    // Should be a cache hit
    bh.consume(ControllerRegistry.getControllers(testPackage))

  @Benchmark
  def checkIsCached(bh: Blackhole): Unit =
    bh.consume(ControllerRegistry.isCached(testPackage))

  @Benchmark
  def checkIsCachedMiss(bh: Blackhole): Unit =
    bh.consume(ControllerRegistry.isCached(nonExistentPackage))
}
