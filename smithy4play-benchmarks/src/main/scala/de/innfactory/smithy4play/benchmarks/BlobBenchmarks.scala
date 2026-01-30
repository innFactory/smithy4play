package de.innfactory.smithy4play.benchmarks

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import smithy4s.Blob

import java.util.concurrent.TimeUnit

/**
 * Benchmarks for lazy blob vs eager blob operations.
 * 
 * Run with: sbt "smithy4playBenchmarks/Jmh/run BlobBenchmarks"
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class BlobBenchmarks {

  // Simulate different payload sizes
  val smallPayload: Array[Byte] = """{"id": 1, "name": "test"}""".getBytes
  val mediumPayload: Array[Byte] = new Array[Byte](1024) // 1KB
  val largePayload: Array[Byte] = new Array[Byte](64 * 1024) // 64KB

  @Setup(Level.Trial)
  def setup(): Unit = {
    // Fill payloads with data
    java.util.Arrays.fill(mediumPayload, 'x'.toByte)
    java.util.Arrays.fill(largePayload, 'x'.toByte)
  }

  @Benchmark
  def createBlobSmall(bh: Blackhole): Unit = {
    bh.consume(Blob(smallPayload))
  }

  @Benchmark
  def createBlobMedium(bh: Blackhole): Unit = {
    bh.consume(Blob(mediumPayload))
  }

  @Benchmark
  def createBlobLarge(bh: Blackhole): Unit = {
    bh.consume(Blob(largePayload))
  }

  @Benchmark
  def createEmptyBlob(bh: Blackhole): Unit = {
    bh.consume(Blob.empty)
  }

  @Benchmark
  def blobSizeCheck(bh: Blackhole): Unit = {
    val blob = Blob(largePayload)
    bh.consume(blob.size)
    bh.consume(blob.isEmpty)
  }

  @Benchmark
  def lazyBlobCreationSimulation(bh: Blackhole): Unit = {
    // Simulate lazy blob creation (only create when needed)
    lazy val blob = Blob(largePayload)
    // Only access size, not the content
    bh.consume(largePayload.length) // This doesn't trigger blob creation
  }

  @Benchmark
  def lazyBlobAccessSimulation(bh: Blackhole): Unit = {
    // Simulate lazy blob access
    lazy val blob = Blob(largePayload)
    bh.consume(blob.toArray) // This triggers blob creation
  }
}
