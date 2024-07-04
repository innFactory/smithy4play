import com.google.inject.AbstractModule
import controller.middlewares.MiddlewareRegistry
import de.innfactory.smithy4play.middleware.MiddlewareRegistryBase
import play.api.libs.concurrent.AkkaGuiceSupport
import _root_.software.amazon.smithy.jsonschema.JsonSchemaMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiJsonSchemaMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol
import _root_.software.amazon.smithy.openapi.fromsmithy.Smithy2OpenApiExtension
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiConverter
import _root_.software.amazon.smithy.openapi.fromsmithy.mappers.*
import _root_.software.amazon.smithy.model.traits.Trait
import alloy.openapi.{AddTags, DataExamplesMapper, DiscriminatedUnions, ExternalDocumentationMapperJsonSchema, ExternalDocumentationMapperOpenApi, NullableMapper, UntaggedUnions}
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.shapes.ShapeId
import scala.jdk.CollectionConverters._
import java.nio.file.{Files, Paths}
import java.util as ju
import java.util.ServiceLoader
import collection.convert.ImplicitConversions._

/** This module handles the bindings for the API to the Slick implementation.
  *
  * https://www.playframework.com/documentation/latest/ScalaDependencyInjection#Programmatic-bindings
  */
class Module extends AbstractModule with AkkaGuiceSupport {

  override def configure(): Unit =
    bind(classOf[MiddlewareRegistryBase]).to(classOf[MiddlewareRegistry])

    

}
