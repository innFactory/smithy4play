package de.innfactory.smithy4play.instrumentation

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation

import java.util.Arrays.asList
import java.util
import java.util.Arrays
import scala.jdk.CollectionConverters.*

class Smithy4PlayInstrumentationModule extends InstrumentationModule("smithy4play") {

  override def isIndyModule: Boolean = false

  override def isHelperClass(className: String): Boolean =
    className.startsWith("io.opentelemetry.javaagent") || className.startsWith("de.innfactory.smithy4play.instrumentation")

  override def getAdditionalHelperClassNames: util.List[String] = List(
    Smithy4PlaySingleton.getClass.getName,
    classOf[TestInstrumentation].getName,
  ).asJava

  override def typeInstrumentations(): util.List[TypeInstrumentation] =
    asList(
      new TestInstrumentation()
    )
}
