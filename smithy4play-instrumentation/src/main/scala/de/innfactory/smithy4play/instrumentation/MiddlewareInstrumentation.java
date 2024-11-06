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


public class MiddlewareInstrumentation implements TypeInstrumentation {


    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
        return hasClassesNamed("de.innfactory.smithy4play.routing.middleware.Smithy4PlayMiddleware");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        System.out.println("- - - MiddlewareInstrumentation typeMatcher - - -");
        return extendsClass(
                named("de.innfactory.smithy4play.routing.middleware.Smithy4PlayMiddleware")
        ).or(
                named("de.innfactory.smithy4play.routing.middleware.Smithy4PlayMiddleware")
        );
    }

    @Override
    public void transform(TypeTransformer transformer) {
        transformer.applyAdviceToMethod(
                named("skipMiddleware")
                        .and(takesArgument(0, named("de.innfactory.smithy4play.routing.context.RoutingContext")))
                        .and(returns(named("java.lang.Boolean"))),
                this.getClass().getName() + "$ApplyAdvice");
    }

    @SuppressWarnings("unused")
    public static class ApplyAdvice {

        @Advice.OnMethodEnter()
        public static void onEnter(
                @Advice.Argument(0) Boolean b,
                @Advice.Local("otelContext") Context context,
                @Advice.Local("otelScope") Scope scope) {
            
            Context parentContext = currentContext();
            Span mySpan = GlobalOpenTelemetry.get().getTracer("smithy4play").spanBuilder("smithy4play.Middleware").startSpan();
            context = mySpan.storeInContext(parentContext);
            Scope myScope = mySpan.makeCurrent();
            scope = myScope;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void stopTraceOnResponse(
                @Advice.This Object currentClass,
                @Advice.Thrown Throwable throwable,
                @Advice.Argument(0) Boolean b,
                @Advice.Return(readOnly = false) Boolean returnValue,
                @Advice.Local("otelContext") Context context,
                @Advice.Local("otelScope") Scope scope) {
            Span mySpan = currentSpan();
            if (scope != null) {
                scope.close();
            }
            if (mySpan != null) {
                mySpan.addEvent("applyMiddleware: " + returnValue);
                mySpan.setAttribute("class", currentClass.getClass().getName());
                mySpan.updateName("smithy4play.Middleware " + currentClass.getClass().getName());
                mySpan.end();
            }
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }
    }
}