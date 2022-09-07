package de.innfactory.smithy4play.client

import play.api.mvc.Headers

import scala.concurrent.Future

case class SmithyClientResponse(
  body: Option[Array[Byte]],
  headers: Map[String, Seq[String]],
  statusCode: Int)


trait RequestClient {
  def send( method: String, path: String, headers: Map[String, Seq[String]], body: Option[Array[Byte]]): Future[SmithyClientResponse]
}
