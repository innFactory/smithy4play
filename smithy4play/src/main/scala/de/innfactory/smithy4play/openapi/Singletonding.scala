package de.innfactory.smithy4play.openapi

import _root_.software.amazon.smithy.jsonschema.JsonSchemaMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiJsonSchemaMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiMapper
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol
import _root_.software.amazon.smithy.openapi.fromsmithy.Smithy2OpenApiExtension
import _root_.software.amazon.smithy.openapi.fromsmithy.OpenApiConverter
import _root_.software.amazon.smithy.openapi.fromsmithy.mappers.*
import _root_.software.amazon.smithy.model.traits.Trait
import alloy.openapi.{AddTags, DataExamplesMapper, DiscriminatedUnions, ExternalDocumentationMapperJsonSchema, ExternalDocumentationMapperOpenApi, NullableMapper, UntaggedUnions}
import _root_.software.amazon.smithy.model.Model
import _root_.software.amazon.smithy.model.loader.ModelAssembler
import _root_.software.amazon.smithy.model.shapes.{Shape, ShapeId}
import scala.jdk.CollectionConverters._
import java.lang.invoke.MethodHandles
import java.nio.file.{Files, Paths}
import java.util as ju
import java.util.ServiceLoader
// import collection.convert.ImplicitConversions._
// import scala.jdk.CollectionConverters._
import collection.convert.ImplicitConversions._
import scala.jdk.CollectionConverters._

object Singletonding {


  try {
    val converter = OpenApiConverter.create()
    val config = converter.getConfig()
    val classLoader = this.getClass.getClassLoader

    Thread.currentThread().setContextClassLoader(classLoader)

    val openApiClassLoader = classOf[OpenApiConverter].getClassLoader

    case class TraitKey[T <: Trait](cls: Class[T]) {
      def getIdIfApplied(shape: Shape): Option[ShapeId] = {
        val maybeTrait = shape.getTrait(cls)
        if (maybeTrait.isPresent()) {
          Some(maybeTrait.get().toShapeId())
        } else None
      }
    }
    val openapiAwareTraits = ServiceLoader
      .load(
        classOf[Smithy2OpenApiExtension],
        classLoader
      )

      for (oa <- openapiAwareTraits) {

        println("-- OA Loader --" + oa.toString)
//        for (protocol <- oa.getProtocols()) {
//          println(protocol.getProtocolType())
//        }


      }


//    val services = ServiceLoader
//      .load(
//        classOf[Smithy2OpenApiExtension],
//        classLoader
//      )
//
//    for (extension <- ServiceLoader.load(classOf[Smithy2OpenApiExtension], classLoader)) {
//      println(extension)
//
//      for (protocol <- extension.getProtocols()) {
//       println(protocol.getProtocolType())
//
//
//        try {
//          protocol.asInstanceOf[OpenApiProtocol[?]].toString
//          println("casted: " + protocol.getProtocolType())
//        } catch {
//          case e => println(e)
//        }
//      }
//
//
//
//    }


    config.setService(ShapeId.from("testDefinitions.test#TestControllerService"))
    config.setProtocol(ShapeId.from("de.innfactory.smithy4play.openapi.protocols#smithy4PlayService"))

    converter.classLoader(classLoader)

    converter
      .convertToNode(Model
        .assembler()
        .discoverModels(classLoader)
        .addImport(Paths.get("./smithy4playTest/testSpecs"))
        .assemble()
        .unwrap());



  } catch {
    case e => println(e)
  }

}
