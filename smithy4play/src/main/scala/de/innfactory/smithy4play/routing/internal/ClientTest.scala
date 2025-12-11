package de.innfactory.smithy4play.routing.internal

import cats.data.{ EitherT, Kleisli }
import cats.implicits.catsSyntaxApplicativeId
import de.innfactory.smithy4play.ContextRoute
import smithy4s.http.*
import play.api.mvc.{ RawBuffer, Request, Result }
import smithy4s.Blob
import smithy4s.client.UnaryClientCodecs
import smithy4s.http.{ HttpUnaryClientCodecs, Metadata }
import smithy4s.interopcats.monadThrowShim
import smithy4s.schema.Schema

import scala.concurrent.{ ExecutionContext, Future }

class ClientTest()(implicit ec: ExecutionContext) {}
