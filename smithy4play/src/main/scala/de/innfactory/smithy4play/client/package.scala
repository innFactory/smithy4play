package de.innfactory.smithy4play

import cats.{ FlatMap, MonadThrow }
import cats.data.{ EitherT, Kleisli }
import smithy4s.{ Blob, Hints }
import smithy4s.http.{ HttpRequest, HttpResponse }

import scala.concurrent.{ ExecutionContext, Future }
import cats.syntax.flatMap.given

package object client {

  case class ClientError(underlying: Throwable, httpResponse: HttpResponse[Throwable]) extends Throwable
  object ClientError {
    def create(t: Throwable, httpResponse: HttpResponse[Blob]): ClientError =
      ClientError.apply(t, httpResponse.copy(body = t))
  }

  type ClientResponse[O]         = EitherT[Future, Throwable, O]                 // Maybe handle all responses
  type FinishedClientResponse[O] = EitherT[Future, ClientError, HttpResponse[O]] // Maybe handle all responses
  type ClientMiddleware          = HttpRequest[Blob] => HttpRequest[Blob]
  type RunnableClientResponse[O] = Kleisli[FinishedClientResponse, ClientMiddleware, O]

  type Context                  = () => HttpResponse[Blob]
  type RunnableClientRequest[O] = Kleisli[ClientResponse, Context, O]

  type ClientFinishedResponse[O] = RunnableClientRequest[HttpResponse[O]]

  case class OWrapper[O](o: O, httpResponse: HttpResponse[Blob])

  def matchStatusCodeForResponse(hints: Hints, httpResponse: HttpResponse[Blob]): Boolean = {
    val httpTag = hints.get(using smithy.api.Http.tagInstance)

    val httpCode           = httpTag.map(_.code).map(v => List(v)).getOrElse(List.empty)
    val allowedStatusCodes = httpCode

    allowedStatusCodes.contains(httpResponse.statusCode) ||
    (httpResponse.statusCode >= 200 && httpResponse.statusCode < 300)
  }

  implicit def monadThrowRunnableClientRequest(implicit ec: ExecutionContext): MonadThrow[ClientResponse] =
    new MonadThrow[ClientResponse] {

      private def left[A](e: Throwable): ClientResponse[A] =
        EitherT.leftT(e)

      private def right[A](a: A): ClientResponse[A] =
        EitherT.rightT(a)

      override def raiseError[A](e: Throwable): ClientResponse[A] = left(e)

      override def handleErrorWith[A](
        fa: ClientResponse[A]
      )(f: Throwable => ClientResponse[A]): ClientResponse[A] =
        fa.leftFlatMap(f(_))

      override def pure[A](x: A): ClientResponse[A] =
        EitherT.rightT(x)

      override def flatMap[A, B](fa: ClientResponse[A])(f: A => ClientResponse[B]): ClientResponse[B] =
        fa.flatMap(f)

      override def tailRecM[A, B](a: A)(f: A => ClientResponse[Either[A, B]]): ClientResponse[B] = ???
//        f(a).flatMap {
//          case Left(value) => tailRecM(value)(f)
//          case Right(value) => Kleisli(ctx => EitherT.rightT(ctx().copy(body = value)))
//        }
    }

}
