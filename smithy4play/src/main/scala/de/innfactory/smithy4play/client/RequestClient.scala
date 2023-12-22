package de.innfactory.smithy4play.client

import play.api.mvc.Headers
import smithy4s.Blob

import scala.concurrent.Future

case class SmithyClientResponse(body: Blob, headers: Map[String, Seq[String]], statusCode: Int)

trait RequestClient {
  def send(
    method: String,
    path: String,
    headers: Map[String, Seq[String]],
    body: Blob
  ): Future[SmithyClientResponse]
}
