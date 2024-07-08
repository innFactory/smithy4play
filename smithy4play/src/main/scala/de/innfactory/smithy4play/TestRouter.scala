package de.innfactory.smithy4play

import com.typesafe.config.Config
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes
import smithy4s.codecs.BlobDecoder
import smithy4s.schema.CachedSchemaCompiler

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestRouter @Inject() (implicit
                            cc: ControllerComponents,
                            app: Application,
                            ec: ExecutionContext,
                            config: Config
                           ) extends AutoRouter {

  override val customDecoders: PartialFunction[ContentType, CachedSchemaCompiler[BlobDecoder]] = {
    logger.debug("[TestRouter] Loading Custom Decoders")
    {
      case v => stringAndBlobDecoder
    }
  }

}
