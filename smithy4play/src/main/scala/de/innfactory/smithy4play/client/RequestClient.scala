package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.EndpointRequest
import play.api.mvc.Headers
import smithy4s.Blob
import smithy4s.http.{ CaseInsensitive, HttpResponse }

import scala.concurrent.Future

trait RequestClient {
  def send(
    method: String,
    path: String,
    headers: Map[CaseInsensitive, Seq[String]],
    request: EndpointRequest
  ): Future[HttpResponse[Blob]]
}
