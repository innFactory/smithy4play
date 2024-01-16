package de.innfactory.smithy4play.client

import play.api.mvc.Headers
import smithy4s.Blob
import smithy4s.http.HttpResponse

import scala.concurrent.Future

trait RequestClient {
  def send(
    method: String,
    path: String,
    headers: Map[String, Seq[String]],
    body: Blob
  ): Future[HttpResponse[Blob]]
}
