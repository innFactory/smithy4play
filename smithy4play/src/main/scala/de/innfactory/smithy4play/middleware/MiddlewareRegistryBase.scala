package de.innfactory.smithy4play.middleware

trait MiddlewareRegistryBase {

  val middlewares: Seq[MiddlewareBase]

}
