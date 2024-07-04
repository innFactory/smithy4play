package de.innfactory.smithy4play.openapi;

import software.amazon.smithy.jsonschema.JsonSchemaMapper;
import software.amazon.smithy.openapi.fromsmithy.OpenApiJsonSchemaMapper;
import software.amazon.smithy.openapi.fromsmithy.OpenApiMapper;
import software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol;
import software.amazon.smithy.openapi.fromsmithy.Smithy2OpenApiExtension;
import software.amazon.smithy.openapi.fromsmithy.mappers.*;
import alloy.openapi.AddTags;
import alloy.openapi.DataExamplesMapper;
import alloy.openapi.DiscriminatedUnions;
import alloy.openapi.ExternalDocumentationMapperJsonSchema;
import alloy.openapi.ExternalDocumentationMapperOpenApi;
import alloy.openapi.NullableMapper;
import alloy.openapi.UntaggedUnions;

import java.util.Arrays;
import java.util.List;

final public class Smithy4PlayOpenApiExtension implements Smithy2OpenApiExtension {

    public Smithy4PlayOpenApiExtension() {
        new SingletonCreator();
        System.out.println("Smithy4PlayOpenApiExtension constructed");
    }

    public List<OpenApiMapper> getOpenApiMappers() {
        return Arrays.asList(
                new CheckForGreedyLabels(),
                new CheckForPrefixHeaders(),
                new OpenApiJsonSubstitutions(),
                new OpenApiJsonAdd(),
                new RemoveUnusedComponents(),
                new UnsupportedTraits(),
                new RemoveEmptyComponents(),
                new AddTags(),
                new ExternalDocumentationMapperOpenApi()
        );
    }


    public  List<OpenApiProtocol<?>> getProtocols() {
        return Arrays.asList(
                new Smithy4PlayOpenApiProtocol()
        );
    }


    public List<JsonSchemaMapper> getJsonSchemaMappers() {
       return  Arrays.asList(
         new OpenApiJsonSchemaMapper(),
          new DiscriminatedUnions(),
          new UntaggedUnions(),
          new DataExamplesMapper(),
          new ExternalDocumentationMapperJsonSchema(),
          new NullableMapper()
        );
    }

//  override def getProtocols(): ju.List[OpenApiProtocol[?]] = List(
//    new Smithy4PlayOpenApiProtocol()
//  ).asJava.asInstanceOf[ju.List[OpenApiProtocol[?]]]
//
//  override def getOpenApiMappers(): ju.List[OpenApiMapper] = List(
//    new CheckForGreedyLabels(),
//    new CheckForPrefixHeaders(),
//    new OpenApiJsonSubstitutions(),
//    new OpenApiJsonAdd(),
//    new RemoveUnusedComponents(),
//    new UnsupportedTraits(),
//    new RemoveEmptyComponents(),
//    new AddTags(),
//    new ExternalDocumentationMapperOpenApi()
//  ).asJava

//  override def getJsonSchemaMappers(): ju.List[JsonSchemaMapper] = {
//    System.out.println("Smithy4PlayOpenApiExtension getJsonSchemaMappers was called")
//    List(
//      new OpenApiJsonSchemaMapper(): JsonSchemaMapper,
//      new DiscriminatedUnions(),
//      new UntaggedUnions(),
//      new DataExamplesMapper(),
//      new ExternalDocumentationMapperJsonSchema(),
//      new NullableMapper()
//    ).asJava
//  }

}