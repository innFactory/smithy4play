package de.innfactory.smithy4play

import de.innfactory.smithy4play.routing.internal.RequestWrapped
import play.api.mvc.{RawBuffer, Request, Result}

package object routing {
  type PlayTransformation = RequestWrapped => ContextRoute[Result]
}
