package de.innfactory.smithy4play.mcp.server.util

import smithy4s.schema.Primitive

object SchemaUtils {

  def primitiveToJsonType(tag: Primitive[?]): String = tag match {
    case Primitive.PInt | Primitive.PShort | Primitive.PLong | Primitive.PByte            => "integer"
    case Primitive.PFloat | Primitive.PDouble | Primitive.PBigDecimal | Primitive.PBigInt => "number"
    case Primitive.PBoolean                                                               => "boolean"
    case Primitive.PString | Primitive.PUUID | Primitive.PBlob | Primitive.PDocument      => "string"
    case Primitive.PTimestamp                                                             => "string"
  }
}
