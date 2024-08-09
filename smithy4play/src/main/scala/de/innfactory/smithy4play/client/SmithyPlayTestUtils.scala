package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.logger
import play.api.libs.json.{ Json, Reads }
import smithy4s.http.HttpResponse

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.concurrent.{ Await, ExecutionContext }

object SmithyPlayTestUtils {

  implicit class EnhancedResponse[O](response: RunnableClientResponse[O]) {
    def awaitRight(implicit
      ec: ExecutionContext,
      timeout: Duration = 5.seconds
    ): HttpResponse[O] = {
      val result = Await
        .result(
          response.run(identity)
            .bimap(
              throwable =>
                logger.error(
                  s"Expected Right, got Left: ${throwable.toString} ${throwable.getMessage} ${throwable.httpResponse.statusCode} Error: ${throwable.underlying.getMessage} ${throwable.underlying.getCause}"
                ),
              identity
            )
            .value,
          timeout
        )
        .toOption
        .get

      result
    }

    def awaitLeft(implicit
      ec: ExecutionContext,
      timeout: Duration = 5.seconds
    ): HttpResponse[Throwable] = {
      val result = Await
        .result(
          response.run(identity)
            .bimap(
              identity,
              res => logger.error(s"Expected Left, got Right: ${res.toString}")
            )
            .value,
          timeout
        )
        .swap
        .toOption
        .get

      result.httpResponse.copy(body = result.underlying)
    }
  }

}
