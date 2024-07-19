package de.innfactory.smithy4play.instrumentation;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentSpan;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.*;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;


import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TestInstrumentation implements TypeInstrumentation {


    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
        return hasClassesNamed("de.innfactory.smithy4play.routing.TestClass");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        System.out.println("- - - TestInstrumentation typeMatcher - - -");
        return named("de.innfactory.smithy4play.routing.TestClass");
    }

    @Override
    public void transform(TypeTransformer transformer) {
        System.out.println("- - - TestInstrumentation transform - - -");

        transformer.applyAdviceToMethod(
                named("test")
                        .and(takesArgument(0, named("java.lang.String")))
                        .and(returns(named("java.lang.String"))),
                this.getClass().getName() + "$ApplyAdvice");
    }

    @SuppressWarnings("unused")
    public static class ApplyAdvice {

        @Advice.OnMethodEnter()
        public static void onEnter(
                @Advice.Argument(0) String test,
                @Advice.Local("otelContext") Context context,
                @Advice.Local("otelScope") Scope scope) {

            System.out.println("TestInstrumentation ApplyAdvice onEnter " + test);
            // span.addEvent("ADVICE smithy4play " + test);
            Context parentContext = currentContext();
            Span mySpan = GlobalOpenTelemetry.get().getTracer("smithy4play").spanBuilder("test span").startSpan();
            System.out.println("TestInstrumentation ApplyAdvice mySpan " + mySpan.getSpanContext().getSpanId());
            context = mySpan.storeInContext(parentContext);
            Scope myScope = mySpan.makeCurrent();
            System.out.println("TestInstrumentation ApplyAdvice should start");
            scope = myScope;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void stopTraceOnResponse(
                @Advice.This Object testClass,
                @Advice.Thrown Throwable throwable,
                @Advice.Argument(0) String test,
                @Advice.Return(readOnly = false) String testout,
                @Advice.Local("otelContext") Context context,
                @Advice.Local("otelScope") Scope scope) {
            System.out.println("TestInstrumentation ApplyAdvice onExit");
            Span mySpan = currentSpan();
            if (scope != null) {
                scope.close();
            }
            if(mySpan != null) {
                mySpan.addEvent(testout);
                mySpan.setAttribute("test", testout);
                mySpan.updateName("TEST / " + testout);
                mySpan.end();
            }
            if (throwable != null) {
                throwable.printStackTrace();
            }
            if (context != null) {
                System.out.println("TestInstrumentation ApplyAdvice onExit update update Span name");
               // Span.fromContext(context).updateName(testout);
            }
        }
    }
}