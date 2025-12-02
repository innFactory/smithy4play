package io.cleverone.mcp.server.util

import play.api.libs.json.JsValue
import smithy4s.Document

object DocumentConverter {

  def documentToJsonString(doc: Document): String = doc match {
    case Document.DNull           => "null"
    case Document.DBoolean(value) => value.toString
    case Document.DNumber(value)  => value.toString
    case Document.DString(value)  => s"\"$value\""
    case Document.DArray(values)  => values.map(documentToJsonString).mkString("[", ",", "]")
    case Document.DObject(fields) =>
      fields.map { case (k, v) => s"\"$k\":${documentToJsonString(v)}" }.mkString("{", ",", "}")
  }

  def documentToJsValue(document: Document): JsValue = {
    import play.api.libs.json.*
    document match {
      case Document.DNumber(value)  => JsNumber(value)
      case Document.DString(value)  => JsString(value)
      case Document.DBoolean(value) => JsBoolean(value)
      case Document.DNull           => JsNull
      case Document.DArray(value)   => JsArray(value.map(documentToJsValue))
      case Document.DObject(value)  => JsObject.apply(value.map(t => (t._1, documentToJsValue(t._2))))
    }
  }

  def jsValueToSmithyDocument(jsValue: JsValue): smithy4s.Document = {
    import smithy4s.Document.*
    import play.api.libs.json.*

    jsValue match {
      case JsNull       => DNull
      case JsBoolean(b) => DBoolean(b)
      case JsNumber(n) =>
        if (n.isValidInt) DNumber(n.toInt)
        else if (n.isValidLong) DNumber(n.toLong)
        else DNumber(n.toDouble)
      case JsString(s)   => DString(s)
      case JsArray(arr)  => DArray(arr.map(jsValueToSmithyDocument).toIndexedSeq)
      case obj: JsObject => DObject(obj.value.view.mapValues(jsValueToSmithyDocument).toMap)
    }
  }
}

