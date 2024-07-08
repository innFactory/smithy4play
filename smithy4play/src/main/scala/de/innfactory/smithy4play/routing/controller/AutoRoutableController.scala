package de.innfactory.smithy4play.routing.controller

import de.innfactory.smithy4play.codecs.Codec
import de.innfactory.smithy4play.routing.middleware.Middleware
import play.api.routing.Router.Routes

private[smithy4play] trait AutoRoutableController {
  def router: (Codec, Middleware) => Routes
}
