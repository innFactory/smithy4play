package de.innfactory.smithy4play.benchmarks

import de.innfactory.smithy4play.codecs.{ CodecSupport, EndpointContentTypes }
import de.innfactory.smithy4play.ContentType
import de.innfactory.smithy4play.meta.ContentTypes
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import smithy4s.Hints

import java.util.concurrent.TimeUnit

/**
 * Benchmarks for codec resolution operations.
 * 
 * Run with: sbt "smithy4playBenchmarks/Jmh/run CodecResolutionBenchmarks"
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class CodecResolutionBenchmarks {

  // JSON-only content types (most common case)
  val jsonOnlyTypes = ContentTypes(general = Some(List("application/json")))
  
  // Multiple content types
  val multipleTypes = ContentTypes(
    general = Some(List("application/json", "application/xml")),
    input = Some(List("application/json")),
    output = Some(List("application/json", "application/xml")),
    error = Some(List("application/json"))
  )

  // Accept headers scenarios
  val noAcceptHeaders: Seq[String] = Seq.empty
  val jsonAcceptHeader: Seq[String] = Seq("application/json")
  val multipleAcceptHeaders: Seq[String] = Seq("application/xml", "application/json", "text/plain")
  
  // Content-Type header scenarios
  val noContentType: Option[String] = None
  val jsonContentType: Option[String] = Some("application/json")

  @Setup(Level.Iteration)
  def clearCache(): Unit = {
    CodecSupport.clearCache()
  }

  @Benchmark
  def resolveJsonOnlyNoHeaders(bh: Blackhole): Unit = {
    bh.consume(CodecSupport.resolveEndpointContentTypes(jsonOnlyTypes, noAcceptHeaders, noContentType))
  }

  @Benchmark
  def resolveJsonOnlyWithJsonAccept(bh: Blackhole): Unit = {
    bh.consume(CodecSupport.resolveEndpointContentTypes(jsonOnlyTypes, jsonAcceptHeader, jsonContentType))
  }

  @Benchmark
  def resolveMultipleTypesNoHeaders(bh: Blackhole): Unit = {
    bh.consume(CodecSupport.resolveEndpointContentTypes(multipleTypes, noAcceptHeaders, noContentType))
  }

  @Benchmark
  def resolveMultipleTypesWithHeaders(bh: Blackhole): Unit = {
    bh.consume(CodecSupport.resolveEndpointContentTypes(multipleTypes, multipleAcceptHeaders, jsonContentType))
  }

  @Benchmark
  def checkIsJsonOnlyEndpoint(bh: Blackhole): Unit = {
    bh.consume(CodecSupport.isJsonOnlyEndpoint(Hints.empty, Hints.empty))
  }

  @Benchmark
  def preComputedJsonOnly(bh: Blackhole): Unit = {
    // Measure the cost of using pre-computed constant
    bh.consume(EndpointContentTypes.JsonOnly)
  }
}
