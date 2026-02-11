package de.innfactory.smithy4play.client.core

import cats.data.Kleisli
import de.innfactory.smithy4play.client.{ ClientError, ClientResponse, FinishedClientResponse, RunnableClientResponse }
import de.innfactory.smithy4play.logger
import smithy4s.Blob
import smithy4s.capability.MonadThrowLike
import smithy4s.client.{ UnaryClientCodecs, UnaryLowLevelClient }
import smithy4s.http.{ HttpRequest, HttpResponse }

import scala.concurrent.ExecutionContext

object Smithy4PlayClientEndpoint {

  def apply[I, E, O, SI, SO](
    lowLevelClient: UnaryLowLevelClient[FinishedClientResponse, HttpRequest[Blob], HttpResponse[Blob]],
    clientCodecs: UnaryClientCodecs[ClientResponse, HttpRequest[Blob], HttpResponse[Blob], I, E, O],
    isSuccessful: HttpResponse[Blob] => Boolean
  )(implicit F: MonadThrowLike[ClientResponse], ec: ExecutionContext): I => RunnableClientResponse[O] = {

    import clientCodecs._
    def inputToRequest(input: I): ClientResponse[HttpRequest[Blob]] =
      inputEncoder(input)

    def mapToClientError(t: Throwable, response: HttpResponse[Blob]): ClientError = ClientError.create(t, response)

    def outputFromResponse(response: HttpResponse[Blob]): FinishedClientResponse[O] =
      if (isSuccessful(response))
        outputDecoder(response).map(v => response.copy(body = v)).leftMap(mapToClientError.curried(_)(response))
      else {
        val error: ClientResponse[O] = F.flatMap(errorDecoder(response))(F.raiseError[O])
        error.leftMap(mapToClientError.curried(_)(response)).map(v => response.copy(body = v))
      }

    (input: I) =>
      Kleisli { mapper =>
        inputToRequest(input)
          .leftMap(t =>
            logger.error(s"Unhandled Error in ${this.getClass.getName}")
            ClientError(t, HttpResponse(0, Map.empty, t))
          )
          .flatMapF { req =>
            lowLevelClient.run(mapper(req))(outputFromResponse).value
          }
      }

  }

}
