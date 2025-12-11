package de.innfactory.smithy4play.codecs

import de.innfactory.smithy4play.{ logger, ContentType }
import de.innfactory.smithy4play.meta.ContentTypes
import play.api.mvc.RequestHeader
import smithy4s.{ Hints, ShapeTag }

object CodecSupport {

  private val tag: ShapeTag[ContentTypes]    = ContentTypes.tagInstance
  private val jsonContentType                = "application/json"
  private val jsonContentTypes: List[String] = List(jsonContentType)

  def resolveSupportedTypes(endpointHints: Hints, serviceHints: Hints): ContentTypes =
    endpointHints
      .get(tag)
      .orElse(serviceHints.get(tag))
      .getOrElse(ContentTypes(general = Some(jsonContentTypes)))

  extension (supported: Option[List[String]])
    private def extractPreferredContentType(default: String, general: Option[List[String]]) =
      supported.orElse(general).flatMap(_.headOption).getOrElse(default)

  extension (supported: Option[List[String]])
    private def findContentType(v: String) =
      supported.flatMap(_.map(_.toLowerCase).find(_ == v.toLowerCase))

  def resolveEndpointContentTypes(
    supportedContentTypes: ContentTypes,
    acceptedTypes: Seq[String],
    contentTypeHeader: Option[String]
  ): EndpointContentTypes = {
    logger.debug(s"[CodecSupport] endpoint supports: $supportedContentTypes")
    logger.debug(s"[CodecSupport] client accepts: $acceptedTypes")
    logger.debug(s"[CodecSupport] contentTypeHeader: $contentTypeHeader")

    val generalContentTypes = supportedContentTypes.general

    val preferredInput: String  = supportedContentTypes.input
      .extractPreferredContentType(jsonContentType, generalContentTypes)
    val preferredOutput: String = supportedContentTypes.output
      .extractPreferredContentType(jsonContentType, generalContentTypes)
    val preferredError: String  = supportedContentTypes.error
      .extractPreferredContentType(jsonContentType, generalContentTypes)

    val accepted         =
      acceptedTypes.find(v => supportedContentTypes.output.orElse(generalContentTypes).findContentType(v).isDefined)
    val errorAccepted    =
      acceptedTypes.find(v => supportedContentTypes.error.orElse(generalContentTypes).findContentType(v).isDefined)
    val inputContentType =
      contentTypeHeader.flatMap(v => supportedContentTypes.input.orElse(generalContentTypes).findContentType(v))

    val contentType = EndpointContentTypes(
      ContentType(inputContentType.getOrElse(preferredInput)),
      ContentType(accepted.getOrElse(preferredOutput)),
      ContentType(errorAccepted.getOrElse(preferredError))
    )

    logger.debug(s"[CodecSupport] determined $contentType")

    contentType
  }

}
