package de.innfactory

import cats.data.{EitherT, Kleisli}
import org.slf4j
import play.api.Logger
import play.api.mvc.{ControllerComponents, RequestHeader}
import play.api.routing.Router.Routes
import smithy4s.Monadic
import smithy4s.http.{CaseInsensitive, HttpEndpoint, PathSegment, matchPath}

import scala.language.experimental.macros
import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.macros.whitebox

package object smithy4play {

  type RouteResult[O] = EitherT[Future, ContextRouteError, O]

  type ContextRoute[O] = Kleisli[RouteResult, RoutingContext, O]

  //change name of logger
  val logger: slf4j.Logger = Logger("play4s").logger

  def getHeaders(req: RequestHeader): Map[CaseInsensitive, Seq[String]] =
    req.headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  def matchRequestPath(
      x: RequestHeader,
      ep: HttpEndpoint[_]
  ): Option[Map[String, String]] = {
    ep.matches(x.path.replaceFirst("/", "").split("/").filter(_.nonEmpty))
  }

  trait AutoRoutableController {
    implicit def transformToRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
        _
    ] <: ContextRoute[_]](
        impl: Monadic[Alg, F]
    )(implicit
        serviceProvider: smithy4s.Service.Provider[Alg, Op],
        ec: ExecutionContext,
        cc: ControllerComponents
    ): Routes = {
      new SmithyPlayRouter[Alg, Op, F](impl).routes()
    }

    val routes: Routes

  }

  @compileTimeOnly("Macro failed to expand. \"Add: scalacOptions += \"-Ymacro-annotations\"\" to project settings")
  class AutoRouting extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro AutoRoutingMacro.impl
  }

  object AutoRoutingMacro {
    def impl(c: whitebox.Context)(annottees: c.Tree*): c.Tree = {
      import c.universe._
      annottees match {
        case List(
              q"$mods class $className $ctorMods(...$paramss) extends { ..$earlydefns } with ..$parentss { $self => ..$body }"
            ) =>
          /*val parsedCc = q"implicit <paramaccessor> private[this] val cc: ControllerComponents = _"
          val params = (paramss :+ parsedCc).distinct
          println(ctorMods)
          println(paramss)
          println(params)*/
          val name: TermName = TermName(className.toString())
          q"""$mods class $className $ctorMods(...$paramss)
                 extends { ..$earlydefns }
                 with ..$parentss
                 with de.innfactory.smithy4play.AutoRoutableController
              { $self =>
                  override val routes: play.api.routing.Router.Routes = this
                ..$body }
                """
        case _ =>
          c.abort(
            c.enclosingPosition,
            "RegisterClass: An AutoRouter Annotation on this type of Class is not supported."
          )
      }
    }
  }



}
