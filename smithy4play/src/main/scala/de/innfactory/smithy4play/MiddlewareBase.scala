package de.innfactory.smithy4play

import cats.data.Kleisli
import play.api.Logger
import smithy4s.Hint

trait MiddlewareBase {

  val logger: Logger = Logger("smithy4play")

  val hint: Hint

  def logic: Kleisli[RouteResult, RoutingContext, RoutingContext]

}
