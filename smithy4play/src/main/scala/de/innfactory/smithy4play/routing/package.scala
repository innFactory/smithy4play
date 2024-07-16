package de.innfactory.smithy4play

import play.api.mvc.{ RawBuffer, Request, Result }

package object routing {
  type PlayTransformation = Request[RawBuffer] => ContextRoute[Result]
}
