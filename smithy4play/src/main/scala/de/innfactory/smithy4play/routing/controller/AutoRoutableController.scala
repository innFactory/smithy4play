package de.innfactory.smithy4play.routing.controller

import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.routing.internal.InternalRoute
import de.innfactory.smithy4play.routing.middleware.Middleware

trait AutoRoutableController {
  private[smithy4play] def router: (Codec, Middleware) => InternalRoute
}
