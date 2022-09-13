package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.ClientResponse
import play.api.libs.json.Json

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.concurrent.{ Await, ExecutionContext, Future }

object SmithyPlayTestUtils {

  implicit class EnhancedResponse[O](response: ClientResponse[O]) {
    def awaitRight(implicit
      ec: ExecutionContext,
      timeout: Duration = 5.seconds
    ): SmithyPlayClientEndpointResponse[O] =
      Await.result(
        response.map(_.toOption.get),
        timeout
      )

    def awaitLeft(implicit
      ec: ExecutionContext,
      timeout: Duration = 5.seconds,
      errorAsString: Boolean = true
    ): SmithyPlayClientEndpointErrorResponse =
      Await.result(
        response.map(_.left.toOption.get),
        timeout
      )
  }

  implicit class EnhancedByteArray(error: Array[Byte]) {
    def toErrorString: String = new String(error)
  }

}
