package models

import scala.xml.{ Elem, Node, Text }

object NodeImplicits {

  implicit class NodeEnhancer(xml: Elem) {
    def normalize: String = {
      def normalizeText(text: String): String = text.trim

      def normalizeElem(node: Node): Node = node match {
        case elem: Elem =>
          val normalizedChildren = elem.child.map {
            case t: Text => Text(normalizeText(t.text))
            case other   => normalizeElem(other)
          }
          elem.copy(child = normalizedChildren)
        case other      => other
      }

      val normalizedXml = normalizeElem(xml)
      normalizedXml.mkString
    }
  }
}
