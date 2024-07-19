package de.innfactory.smithy4play.instrumentation

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation

import java.util.Arrays.asList
import java.util
import java.util.Arrays
import scala.jdk.CollectionConverters.*

class Smithy4PlayInstrumentationModule extends InstrumentationModule("smithy4play"){
  
  Smithy4PlaySingleton

 override def typeInstrumentations(): util.List[TypeInstrumentation] = {
   asList(
     new TestInstrumentation(),
   )
  }
}
