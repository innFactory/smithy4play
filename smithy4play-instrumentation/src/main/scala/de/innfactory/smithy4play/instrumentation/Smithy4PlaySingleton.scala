package de.innfactory.smithy4play.instrumentation

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter

object Smithy4PlaySingleton {

  private val SPAN_NAME = "play.request"
  private val INSTRUMENTER = Instrumenter.builder[Void, Void](GlobalOpenTelemetry.get, "io.opentelemetry.smtihyt4play", (s) => SPAN_NAME).buildInstrumenter
  def instrumenter = INSTRUMENTER
}
