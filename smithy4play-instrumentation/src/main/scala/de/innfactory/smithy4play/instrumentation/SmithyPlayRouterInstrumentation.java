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

public class SmithyPlayRouterInstrumentation implements TypeInstrumentation {


    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
        return hasClassesNamed("de.innfactory.smithy4play.routing.internal.Smithy4PlayRouterHandler");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
       // System.out.println("- - - SmithyPlayRouterInstrumentation typeMatcher - - -");
        return named("de.innfactory.smithy4play.routing.internal.Smithy4PlayRouterHandler");
    }

    @Override
    public void transform(TypeTransformer transformer) {
        transformer.applyAdviceToMethod(
                named("handleForInstrument"),
                this.getClass().getName() + "$ApplyAdvice");
        //System.out.println("- - - SmithyPlayRouterInstrumentation transform - - -");
    }

    @SuppressWarnings("unused")
    public static class ApplyAdvice {

        @Advice.OnMethodEnter()
        public static void onEnter(
                @Advice.Argument(0) java.lang.String path,
                @Advice.Argument(1) java.lang.String method,
                @Advice.Local("otelContext") Context context,
                @Advice.Local("otelScope") Scope scope) {
            //System.out.println("ApplyAdvice Start applyHandler");
            Context parentContext = currentContext();
            Span mySpan = GlobalOpenTelemetry.get().getTracer("smithy4play").spanBuilder("smithy4play.Smithy4PlayRouter").startSpan();
            context = mySpan.storeInContext(parentContext);
            Scope myScope = mySpan.makeCurrent();
            scope = myScope;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void stopTraceOnResponse(
                @Advice.This Object currentClass,
                @Advice.Thrown Throwable throwable,
                @Advice.Argument(0) java.lang.String path,
                @Advice.Argument(1) java.lang.String method,
                @Advice.Return(readOnly = false) java.lang.String returnValue,
                @Advice.Local("otelContext") Context context,
                @Advice.Local("otelScope") Scope scope) {
            //System.out.println("ApplyAdvice End applyHandler");
            Span mySpan = currentSpan();
            if (scope != null) {
                scope.close();
            }
            if (mySpan != null) {
                String routeName = path;
                String methodName = method;
                mySpan.setAttribute("class", currentClass.getClass().getName());
                mySpan.updateName(methodName + " " + routeName);
                mySpan.setAttribute("http.request.method", methodName);
                mySpan.setAttribute("http.request.url", routeName);
                mySpan.end();
            }
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }
    }
}