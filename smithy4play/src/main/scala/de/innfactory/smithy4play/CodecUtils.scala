package de.innfactory.smithy4play

import smithy4s.{ HintMask, Schema }
import smithy4s.http.{ CodecAPI, PayloadError }
import smithy4s.http.json.codecs
import smithy4s.internals.InputOutput

object CodecUtils {

  private val codecs: codecs =
    smithy4s.http.json.codecs(alloy.SimpleRestJson.protocol.hintMask ++ HintMask(InputOutput), 4096)

  def writeInputToBody[I](input: I, inputSchema: Schema[I], codecAPI: CodecAPI): Array[Byte] = {
    val codec = codecAPI.compileCodec(inputSchema)
    codecAPI.writeToArray(codec, input)
  }

  def readFromBytes[E](data: Array[Byte], inputSchema: Schema[E], codecAPI: CodecAPI): Either[PayloadError, E] =
    codecAPI.decodeFromByteArray(codecAPI.compileCodec(inputSchema), data)

  def writeEntityToJsonBytes[E](e: E, schema: Schema[E]) = writeInputToBody[E](e, schema, codecs)

  def readFromJsonBytes[E](bytes: Array[Byte], schema: Schema[E]): Option[E] =
    readFromBytes(bytes, schema, codecs).toOption

  def extractCodec(headers: Map[String, Seq[String]]): CodecAPI = {
    val contentType =
      headers.getOrElse("content-type", List("application/json"))
    val codecApi    = contentType match {
      case List("application/json") => codecs
      case _                        => CodecAPI.nativeStringsAndBlob(codecs)
    }
    codecApi
  }
}
