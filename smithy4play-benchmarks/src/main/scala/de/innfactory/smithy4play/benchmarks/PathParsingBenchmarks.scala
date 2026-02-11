package de.innfactory.smithy4play.benchmarks

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit

/** Benchmarks for path parsing operations.
  *
  * Run with: sbt "smithy4playBenchmarks/Jmh/run PathParsingBenchmarks"
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class PathParsingBenchmarks {

  // Test paths of varying complexity
  val simplePath  = "/users"
  val mediumPath  = "/api/v1/users/123"
  val complexPath = "/api/v1/organizations/org-123/projects/proj-456/resources/res-789/actions"
  val rootPath    = "/"
  val emptyPath   = ""

  /** Optimized path deconstruction implementation (mirrors the internal one)
    */
  private def deconstructPath(path: String): IndexedSeq[String] =
    if (path == null || path.isEmpty || path == "/") {
      IndexedSeq.empty
    } else {
      val startIdx = if (path.charAt(0) == '/') 1 else 0
      val endIdx   = path.length

      if (startIdx >= endIdx) {
        IndexedSeq.empty
      } else {
        var segmentCount = 1
        var i            = startIdx
        while (i < endIdx) {
          if (path.charAt(i) == '/') segmentCount += 1
          i += 1
        }

        val segments = new Array[String](segmentCount)
        var segIdx   = 0
        var segStart = startIdx
        i = startIdx

        while (i < endIdx) {
          if (path.charAt(i) == '/') {
            if (i > segStart) {
              segments(segIdx) = path.substring(segStart, i)
              segIdx += 1
            }
            segStart = i + 1
          }
          i += 1
        }

        if (segStart < endIdx) {
          segments(segIdx) = path.substring(segStart, endIdx)
          segIdx += 1
        }

        if (segIdx == segmentCount) {
          segments.toIndexedSeq
        } else {
          segments.take(segIdx).toIndexedSeq
        }
      }
    }

  /** Original naive implementation for comparison
    */
  private def deconstructPathNaive(path: String): IndexedSeq[String] =
    path.replaceFirst("/", "").split("/").filter(_.nonEmpty).toIndexedSeq

  @Benchmark
  def parseSimplePathOptimized(bh: Blackhole): Unit =
    bh.consume(deconstructPath(simplePath))

  @Benchmark
  def parseSimplePathNaive(bh: Blackhole): Unit =
    bh.consume(deconstructPathNaive(simplePath))

  @Benchmark
  def parseMediumPathOptimized(bh: Blackhole): Unit =
    bh.consume(deconstructPath(mediumPath))

  @Benchmark
  def parseMediumPathNaive(bh: Blackhole): Unit =
    bh.consume(deconstructPathNaive(mediumPath))

  @Benchmark
  def parseComplexPathOptimized(bh: Blackhole): Unit =
    bh.consume(deconstructPath(complexPath))

  @Benchmark
  def parseComplexPathNaive(bh: Blackhole): Unit =
    bh.consume(deconstructPathNaive(complexPath))

  @Benchmark
  def parseRootPath(bh: Blackhole): Unit =
    bh.consume(deconstructPath(rootPath))

  @Benchmark
  def parseEmptyPath(bh: Blackhole): Unit =
    bh.consume(deconstructPath(emptyPath))
}
