package de.innfactory
package smithy4play
package client
import play.api.mvc.Headers
import smithy4s.http.json.codecs
import smithy4s.{ Endpoint, HintMask, Schema }
import smithy4s.http.{
  BodyPartial,
  CaseInsensitive,
  CodecAPI,
  HttpContractError,
  HttpEndpoint,
  Metadata,
  MetadataError,
  PayloadError
}
import smithy4s.internals.InputOutput

import scala.concurrent.{ ExecutionContext, Future }

private[smithy4play] class SmithyPlayClientEndpoint[Op[_, _, _, _, _], I, E, O, SI, SO](
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  baseUri: String,
  authHeader: Option[String],
  httpEndpoint: HttpEndpoint[I],
  input: I
)(implicit executionContext: ExecutionContext, client: RequestClient) {

  private val codecs: codecs =
    smithy4s.http.json.codecs(smithy4s.api.SimpleRestJson.protocol.hintMask ++ HintMask(InputOutput))

  private val inputSchema: Schema[I]  = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataEncoder  =
    Metadata.Encoder.fromSchema(inputSchema)
  private val outputMetadataDecoder =
    Metadata.PartialDecoder.fromSchema(outputSchema)
  private val inputHasBody          =
    Metadata.TotalDecoder.fromSchema(inputSchema).isEmpty

  def send(
  ): Future[Either[SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse[O]]] = {
    val metadata           = inputMetadataEncoder.encode(input)
    val path               = buildPath(metadata)
    val headers            = metadata.headers.map(x => (x._1.toString, x._2))
    val headersWithAuth    = if (authHeader.isDefined) headers + ("Authorization" -> Seq(authHeader.get)) else headers
    val code               = httpEndpoint.code
    val codecApi: CodecAPI = extractCodec(headers)
    val send               = client.send(httpEndpoint.method.toString, path, headersWithAuth, _)
    val response           = if (inputHasBody) {
      val codec       = codecApi.compileCodec(inputSchema)
      val bodyEncoded = codecApi.writeToArray(codec, input)
      send(Some(bodyEncoded))
    } else send(None)
    decodeResponse(response, code)
  }

  private def extractCodec(headers: Map[String, Seq[String]]): CodecAPI = {
    val contentType =
      headers.getOrElse("Content-Type", List("application/json"))
    val codecApi    = contentType match {
      case List("application/json") => codecs
      case _                        => CodecAPI.nativeStringsAndBlob(codecs)
    }
    codecApi
  }

  private def decodeResponse(
    response: Future[SmithyClientResponse],
    expectedCode: Int
  ): Future[Either[SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse[O]]] =
    for {
      res     <- response
      codecApi = extractCodec(res.headers)
      metadata = Metadata(headers = res.headers.map(headers => (CaseInsensitive(headers._1), headers._2)))
      output  <- Future(outputMetadataDecoder.total match {
                   case Some(totalDecoder) =>
                     totalDecoder.decode(metadata)
                   case None               =>
                     for {
                       metadataPartial <- outputMetadataDecoder.decode(metadata)
                       bodyPartial     <-
                         codecApi.decodeFromByteArrayPartial(codecApi.compileCodec(outputSchema), res.body.get)
                     } yield metadataPartial.combine(bodyPartial)
                 })
    } yield output
      .map(o => SmithyPlayClientEndpointResponse[O](res.body.map(_ => o), res.headers, res.statusCode, expectedCode))
      .left
      .map {
        case e: PayloadError      =>
          SmithyPlayClientEndpointErrorResponse(e.expected, res.statusCode, expectedCode)
        case error: MetadataError =>
          SmithyPlayClientEndpointErrorResponse(error.getMessage(), res.statusCode, expectedCode)
      }

  def buildPath(metadata: Metadata): String =
    baseUri + httpEndpoint.path(input).mkString("/") + metadata.queryFlattened
      .map(s => s._1 + "=" + s._2)
      .mkString("?", "&", "")

}
