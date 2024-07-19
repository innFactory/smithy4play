package de.innfactory.smithy4play.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer

private[smithy4play] object Telemetry {

  lazy val openTelemetry: OpenTelemetry = GlobalOpenTelemetry.get()

  lazy val tracer: Tracer = openTelemetry.getTracerProvider.get("de.innfactory.smithy4play")
  lazy val metric: Meter = openTelemetry.getMeter("de.innfactory.smithy4play")

}
