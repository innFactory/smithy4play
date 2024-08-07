package de.innfactory.smithy4play

import cats.{FlatMap, MonadThrow}
import cats.data.{EitherT, Kleisli}
import smithy4s.{Blob, Hints}
import smithy4s.http.HttpResponse

import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.flatMap.given

package object client {
  
  case class ClientError(underlying: Throwable, httpResponse: HttpResponse[Blob]) extends Throwable
  
  type ClientResponse[O] = EitherT[Future, ClientError, O] // Maybe handle all responses
  type Context = () => HttpResponse[Blob]
  type RunnableClientRequest[O] = Kleisli[ClientResponse, Context, O]
  type RunnableClientResponse[O] = Kleisli[ClientResponse, Nothing, O]
  
  type ClientFinishedResponse[O] = RunnableClientRequest[HttpResponse[O]]
  
  case class OWrapper[O](o: O, httpResponse: HttpResponse[Blob])
  
  def matchStatusCodeForResponse(hints: Hints, httpResponse: HttpResponse[Blob]): Boolean = {
    val httpTag = hints.get(smithy.api.Http.tagInstance)
    if (httpTag.isDefined) {
      httpTag.get.code == httpResponse.statusCode
    } else {
      false
    }
  }
  
//  implicit def flatMapClientResponse[O]: FlatMap[EitherT[Future, ClientError, HttpResponse[O]] = new FlatMap[EitherT[Future, ClientError, HttpResponse[O]]] {
//    override def flatMap[A, B](fa: ClientResponse[A])(f: A => ClientResponse[B]): ClientResponse[B] = fa.flatMap(f)
//
//    override def tailRecM[A, B](a: A)(f: A => ClientResponse[Either[A, B]]): ClientResponse[B] = ???
//
//    override def map[A, B](fa: ClientResponse[A])(f: A => B): ClientResponse[B] = fa.map(f)
//  }

  implicit def monadThrowRunnableClientRequest(implicit ec: ExecutionContext): MonadThrow[RunnableClientRequest] =
    new MonadThrow[RunnableClientRequest] {

      private def left[A](e: Throwable): RunnableClientRequest[A] = Kleisli { ctx =>
        EitherT.leftT(ClientError(e, ctx.apply()))
      }

      private def right[A](a: A): RunnableClientRequest[A] = Kleisli { ctx =>
        EitherT.rightT(a)
      }

      override def raiseError[A](e: Throwable): RunnableClientRequest[A] = left(e)

      override def handleErrorWith[A](
                                       fa: RunnableClientRequest[A]
                                     )(f: Throwable => RunnableClientRequest[A]): RunnableClientRequest[A] = Kleisli { ctx =>
        fa(ctx).biflatMap(v => f(v).run(ctx), o => EitherT.rightT(o))
      }

      override def pure[A](x: A): RunnableClientRequest[A] = Kleisli { ctx =>
        EitherT.rightT(x)
      }

      override def flatMap[A, B](fa: RunnableClientRequest[A])(f: A => RunnableClientRequest[B]): RunnableClientRequest[B] =
        fa.flatMap(f)

      override def tailRecM[A, B](a: A)(f: A => RunnableClientRequest[Either[A, B]]): RunnableClientRequest[B] = ???
//        f(a).flatMap {
//          case Left(value) => tailRecM(value)(f)
//          case Right(value) => Kleisli(ctx => EitherT.rightT(ctx().copy(body = value)))
//        }
    }


   
}
