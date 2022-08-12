package de.innfactory.smithy4play

import scala.reflect.macros.whitebox

object AutoRoutingMacro {
  def impl(c: whitebox.Context)(annottees: c.Tree*): c.Tree = {
    import c.universe._
    annottees match {
      case List(
            q"$mods class $className $ctorMods(...$paramss) extends { ..$earlydefns } with ..$parentss { $self => ..$body }"
          ) =>
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
