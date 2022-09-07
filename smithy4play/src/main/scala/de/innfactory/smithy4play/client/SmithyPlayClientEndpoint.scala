package de.innfactory
package smithy4play
package client
import play.api.mvc.Headers
import smithy4s.http.json.codecs
import smithy4s.{ Endpoint, HintMask, Schema }
import smithy4s.http.{ BodyPartial, CodecAPI, HttpEndpoint, Metadata, PayloadError }
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

  private val inputMetadataEncoder =
    Metadata.Encoder.fromSchema(inputSchema)
  private val inputHasBody         =
    Metadata.TotalDecoder.fromSchema(inputSchema).isEmpty
  private val outputHasBody =
    Metadata.TotalDecoder.fromSchema(outputSchema).isEmpty


  def send(
  ): Future[Either[SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse[O]]] = {
    val metadata        = inputMetadataEncoder.encode(input)
    val path            = buildPath(metadata)
    val headers         = metadata.headers.map(x => (x._1.toString, x._2))
    val headersWithAuth = if (authHeader.isDefined) headers + ("Authorization" -> Seq(authHeader.get)) else headers
    val code            = httpEndpoint.code
    val contentType     = {
      headers.getOrElse("Content-Type", List("application/json"))
    }
    println(contentType)
    val codecApi        = contentType match {
      case List("application/json") => codecs
      case _                  => CodecAPI.nativeStringsAndBlob(codecs)
    }
    val send            = client.send(httpEndpoint.method.toString, path, headersWithAuth, _)
    val response        = if (inputHasBody) {
      val codec       = codecApi.compileCodec(inputSchema)
      val bodyEncoded = codecApi.writeToArray(codec, input)

      send(Some(bodyEncoded))
    } else send(None)

    println("After Response")

    val nativeCodec: CodecAPI = CodecAPI.nativeStringsAndBlob(codecs)
    response.map { res =>
      nativeCodec
        .decodeFromByteArray(nativeCodec.compileCodec(outputSchema), res.body.getOrElse(Array.emptyByteArray))
        .map(o => SmithyPlayClientEndpointResponse[O](res.body.map(_ => o), res.headers, res.statusCode, code))
        .left
        .map(e => SmithyPlayClientEndpointErrorResponse(e.expected, res.statusCode, code))
    }
  }

  def buildPath(metadata: Metadata): String =
    baseUri + httpEndpoint.path(input).mkString("/") + metadata.queryFlattened
      .map(s => s._1 + "=" + s._2)
      .mkString("?", "&", "")

}
