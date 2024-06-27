package de.innfactory.smithy4play.openapi

import _root_.software.amazon.smithy.jsonschema.JsonSchemaMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiJsonSchemaMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol
import _root_.software.amazon.smithy.openapi.fromsmithy.Smithy2OpenApiExtension
import _root_.software.amazon.smithy.openapi.fromsmithy.mappers.*
import alloy.openapi.{AddTags, DataExamplesMapper, DiscriminatedUnions, ExternalDocumentationMapperJsonSchema, ExternalDocumentationMapperOpenApi, NullableMapper, UntaggedUnions}

import java.util as ju
import scala.jdk.CollectionConverters.*

final class Smithy4PlayOpenApiExtension() extends Smithy2OpenApiExtension {

  override def getProtocols(): ju.List[OpenApiProtocol[?]] = List(
    new Smithy4PlayOpenApiProtocol()
  ).asJava.asInstanceOf[ju.List[OpenApiProtocol[?]]]

  override def getOpenApiMappers(): ju.List[OpenApiMapper] = List(
    new CheckForGreedyLabels(),
    new CheckForPrefixHeaders(),
    new OpenApiJsonSubstitutions(),
    new OpenApiJsonAdd(),
    new RemoveUnusedComponents(),
    new UnsupportedTraits(),
    new RemoveEmptyComponents(),
    new AddTags(),
    new ExternalDocumentationMapperOpenApi()
  ).asJava

  override def getJsonSchemaMappers(): ju.List[JsonSchemaMapper] = List(
    new OpenApiJsonSchemaMapper(): JsonSchemaMapper,
    new DiscriminatedUnions(),
    new UntaggedUnions(),
    new DataExamplesMapper(),
    new ExternalDocumentationMapperJsonSchema(),
    new NullableMapper()
  ).asJava

}