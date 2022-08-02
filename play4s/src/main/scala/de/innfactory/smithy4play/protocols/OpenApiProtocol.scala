package de.innfactory.smithy4play.protocols

import software.amazon.smithy.openapi.fromsmithy.protocols.Smithy4sAbstractRestProtocol
import software.amazon.smithy.jsonschema.Schema
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding.Location
import software.amazon.smithy.model.knowledge._
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes._
import software.amazon.smithy.model.traits._
import software.amazon.smithy.openapi.{OpenApiConfig, OpenApiException}
import software.amazon.smithy.openapi.fromsmithy.Context
import software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol
import software.amazon.smithy.openapi.fromsmithy.OpenApiProtocol.Operation
import software.amazon.smithy.openapi.model._

import scala.jdk.CollectionConverters.CollectionHasAsScala

class OpenApiProtocol
  extends Smithy4sAbstractRestProtocol[RestProtocolTrait] {

  override def getProtocolType(): Class[RestProtocolTrait] =
    classOf[RestProtocolTrait]

  def getDocumentMediaType(): String = "application/json"

  override def updateDefaultSettings(
                                      model: Model,
                                      config: OpenApiConfig
                                    ): Unit = {
    config.setUseJsonName(true);
    config.setDefaultTimestampFormat(TimestampFormatTrait.Format.DATE_TIME);
    config.setUseIntegerType(true);
  }

  def createDocumentSchema(
                            context: Context[RestProtocolTrait],
                            shape: Shape,
                            bindings: List[HttpBinding],
                            mt: Smithy4sAbstractRestProtocol.MessageType
                          ): Schema =
    if (bindings.isEmpty) Schema.builder().`type`("object").build()
    else {

      // We create a synthetic structure shape that is passed through the
      // JSON schema converter. This shape only contains members that make
      // up the "document" members of the input/output/error shape.
      val container: ShapeId = bindings.head.getMember.getContainer
      val containerShape =
        context.getModel.expectShape(container, classOf[StructureShape])

      // Path parameters of requests are handled in "parameters" and headers are
      // handled in headers, so this method must ensure that only members that
      // are sent in the document payload are present in the structure when it is
      // converted to OpenAPI. This ensures that any path parameters are removed
      // before converting the structure to a synthesized JSON schema object.
      // Doing this sanitation after converting the shape to JSON schema might
      // result in things like "required" properties pointing to members that
      // don't exist
      val documentMemberNames = bindings.map(_.getMemberName()).toSet

      val containershapeBuilder = containerShape.toBuilder
      for (
        memberName <- containerShape.getAllMembers.keySet().asScala
        if !documentMemberNames(memberName)
      ) {
        containershapeBuilder.removeMember(memberName)
      }

      val cleanedShape = containershapeBuilder.build()
      context
        .getJsonSchemaConverter
        .convertShape(cleanedShape)
        .getRootSchema
    }

}