package de.innfactory.smithy4play.codecs

import de.innfactory.smithy4play.{ logger, ContentType }
import de.innfactory.smithy4play.meta.ContentTypes
import play.api.http.MimeTypes
import play.api.mvc.RequestHeader
import smithy4s.{ Hints, ShapeTag }

import java.util.concurrent.ConcurrentHashMap

object CodecSupport {

  private val tag: ShapeTag[ContentTypes]    = ContentTypes.tagInstance
  private val jsonContentType                = MimeTypes.JSON
  private val jsonContentTypes: List[String] = List(jsonContentType)

  // Cache for resolved content types per endpoint
  private val contentTypeCache = new ConcurrentHashMap[Int, Option[EndpointContentTypes]]()

  /** Check if the endpoint only supports JSON content type. Used for optimization - JSON-only endpoints can use
    * pre-computed codecs.
    */
  def isJsonOnlyEndpoint(endpointHints: Hints, serviceHints: Hints): Boolean = {
    val supportedTypes = resolveSupportedTypes(endpointHints, serviceHints)
    val generalTypes   = supportedTypes.general.getOrElse(jsonContentTypes)

    def isJsonOnlyList(types: Option[List[String]]): Boolean =
      types.forall(_.forall(_.equalsIgnoreCase(jsonContentType)))

    generalTypes.forall(_.equalsIgnoreCase(jsonContentType)) &&
    isJsonOnlyList(supportedTypes.input) &&
    isJsonOnlyList(supportedTypes.output) &&
    isJsonOnlyList(supportedTypes.error)
  }

  def resolveSupportedTypes(endpointHints: Hints, serviceHints: Hints): ContentTypes =
    endpointHints
      .get(using tag)
      .orElse(serviceHints.get(using tag))
      .getOrElse(ContentTypes(general = Some(jsonContentTypes)))

  extension (supported: Option[List[String]])
    private def extractPreferredContentType(default: String, general: Option[List[String]]) =
      supported.orElse(general).flatMap(_.headOption).getOrElse(default)

  extension (supported: Option[List[String]])
    private def findContentType(v: String) =
      supported.flatMap(_.map(_.toLowerCase).find(_ == v.toLowerCase))

  /** Resolve endpoint content types with caching for repeated requests.
    *
    * For JSON-only endpoints (most common case), returns the pre-computed EndpointContentTypes.JsonOnly instance.
    */
  def resolveEndpointContentTypes(
    supportedContentTypes: ContentTypes,
    acceptedTypes: Seq[String],
    contentTypeHeader: Option[String]
  ): EndpointContentTypes = {
    // Fast path: if no specific accepted types and no content-type header,
    // and supported types are JSON-only, return pre-computed instance
    if (acceptedTypes.isEmpty && contentTypeHeader.isEmpty) {
      val generalTypes = supportedContentTypes.general.getOrElse(jsonContentTypes)
      if (
        generalTypes.forall(_.equalsIgnoreCase(jsonContentType)) &&
        supportedContentTypes.input.forall(_.forall(_.equalsIgnoreCase(jsonContentType))) &&
        supportedContentTypes.output.forall(_.forall(_.equalsIgnoreCase(jsonContentType))) &&
        supportedContentTypes.error.forall(_.forall(_.equalsIgnoreCase(jsonContentType)))
      ) {
        return EndpointContentTypes.JsonOnly
      }
    }

    val cacheKey = (supportedContentTypes, acceptedTypes, contentTypeHeader).hashCode()

    val cached = contentTypeCache.get(cacheKey)
    if (cached != null) {
      return cached.getOrElse(computeContentTypes(supportedContentTypes, acceptedTypes, contentTypeHeader))
    }

    val result = computeContentTypes(supportedContentTypes, acceptedTypes, contentTypeHeader)
    contentTypeCache.putIfAbsent(cacheKey, Some(result))
    result
  }

  private def computeContentTypes(
    supportedContentTypes: ContentTypes,
    acceptedTypes: Seq[String],
    contentTypeHeader: Option[String]
  ): EndpointContentTypes = {
    if (logger.isDebugEnabled) {
      logger.debug(s"[CodecSupport] endpoint supports: $supportedContentTypes")
      logger.debug(s"[CodecSupport] client accepts: $acceptedTypes")
      logger.debug(s"[CodecSupport] contentTypeHeader: $contentTypeHeader")
    }

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

    if (logger.isDebugEnabled) {
      logger.debug(s"[CodecSupport] determined $contentType")
    }

    contentType
  }

  def clearCache(): Unit =
    contentTypeCache.clear()

}
