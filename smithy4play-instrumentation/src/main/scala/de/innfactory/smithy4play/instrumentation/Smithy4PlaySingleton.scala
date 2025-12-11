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
  
}
