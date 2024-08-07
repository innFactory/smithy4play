package de.innfactory.smithy4play.instrumentation

import cats.data.{EitherT, Kleisli}
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.{Instrumenter, SpanKindExtractor}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.chaining.*
object Smithy4PlaySingleton {

  private val SPAN_NAME    = "play.request"

  private val spanKindExtractor = SpanKindExtractor.alwaysServer()
  private val INSTRUMENTER = Instrumenter
    .builder[Void, Void](GlobalOpenTelemetry.get, "io.opentelemetry.smtihyt4play", (s) => SPAN_NAME)
    .setEnabled(true)
    .buildInstrumenter(spanKindExtractor)

    .pipe { (v: Instrumenter[Void, Void]) =>
      println(v.toString)
      println("shouldstart: " + v.shouldStart(Context.root(), null))
      v
    }

  def test()               = {
    println("Smithy4PlaySingleton test")
  }

  def shouldStart(context: Context): Boolean = {
    try {
      INSTRUMENTER.shouldStart(context, null)
    } catch
      case e: Exception => {
        e.printStackTrace()
        println(e.getMessage)
        println(e.getCause)
        false
      }

  }

  def instrumenter()         = INSTRUMENTER
}
