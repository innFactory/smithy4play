package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.{ logger, ClientResponse, RoutingErrorResponse }
import play.api.libs.json.Json

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.concurrent.{ Await, ExecutionContext }

object SmithyPlayTestUtils {

  implicit class EnhancedResponse[O](response: ClientResponse[O]) {
    def awaitRight(implicit
      ec: ExecutionContext,
      timeout: Duration = 5.seconds
    ): SmithyPlayClientEndpointResponse[O] =
      Await.result(
        response.map { res =>
          if (res.isLeft) logger.error(s"Expected Right, got Left: ${res.left.toOption.get.toString}")
          res.toOption.get
        },
        timeout
      )

    def awaitLeft(implicit
      ec: ExecutionContext,
      timeout: Duration = 5.seconds,
      errorAsString: Boolean = true
    ): SmithyPlayClientEndpointErrorResponse =
      Await.result(
        response.map { res =>
          if (res.isRight) logger.error(s"Expected Left, got Right: ${res.toOption.get.toString}")
          res.left.toOption.get
        },
        timeout
      )
  }

  implicit class EnhancedSmithyPlayClientEndpointErrorResponse(errorResponse: SmithyPlayClientEndpointErrorResponse) {
    def toErrorResponse: RoutingErrorResponse = errorResponse.error.toErrorResponse
  }

  implicit class EnhancedByteArray(error: Array[Byte]) {
    def toErrorString: String                 = new String(error)
    def toErrorResponse: RoutingErrorResponse = Json.parse(error).as[RoutingErrorResponse]
  }

}
