package de.innfactory.smithy4play

import cats.{FlatMap, MonadThrow}
import cats.data.{EitherT, Kleisli}
import smithy4s.{Blob, Hints}
import smithy4s.http.HttpResponse
import cats.implicits.*

import scala.concurrent.{ExecutionContext, Future}

package object client {
  type ClientResponse[O] = EitherT[Future, (HttpResponse[Blob], Throwable), (HttpResponse[Blob], O)] // Maybe handle all responses
  type Context = () => HttpResponse[Blob]
  type RunnableClientRequest[O] = Kleisli[ClientResponse, Context, O]
  type ClientRequest[O] = Kleisli[ClientResponse, Unit, O]
  
  case class OWrapper[O](o: O, httpResponse: HttpResponse[Blob])
  
  implicit def flatMapClientResponse: FlatMap[ClientResponse] = new FlatMap[ClientResponse] {}


  def matchStatusCodeForResponse(hints: Hints, httpResponse: HttpResponse[Blob]): Boolean = {
    val httpTag = hints.get(smithy.api.Http.tagInstance)
    if (httpTag.isDefined) {
      httpTag.get.code == httpResponse.statusCode
    } else {
      false
    }
  }

  implicit def monadThrowRunnableClientRequest(implicit ec: ExecutionContext): MonadThrow[RunnableClientRequest] =
    new MonadThrow[RunnableClientRequest] {

      private def left[A](e: Throwable): RunnableClientRequest[A] = Kleisli { ctx =>
        EitherT.leftT[Future, (HttpResponse[Blob], A)]((ctx.apply(), e))
      }

      private def right[A](a: A): RunnableClientRequest[A] = Kleisli { ctx =>
        EitherT.rightT[Future, (HttpResponse[Blob], Throwable)]((ctx.apply(), a))
      }

           override def raiseError[A](e: Throwable): RunnableClientRequest[A] = left(e)

      override def handleErrorWith[A](
                                       fa: RunnableClientRequest[A]
                                     )(f: Throwable => RunnableClientRequest[A]): RunnableClientRequest[A] = Kleisli { ctx =>
        fa(ctx).biflatMap(v => f(v._2)(ctx), o => EitherT.rightT(o))
      }

      override def pure[A](x: A): RunnableClientRequest[A] = right(x)

      override def flatMap[A, B](fa: RunnableClientRequest[A])(f: A => RunnableClientRequest[B]): RunnableClientRequest[B] =
        fa.flatMap(f)

      override def tailRecM[A, B](a: A)(f: A => RunnableClientRequest[Either[A, B]]): RunnableClientRequest[B] =
        f(a).flatMap {
          case Left(value) => tailRecM(value)(f)
          case Right(value) => Kleisli(ctx => EitherT.rightT((ctx(), value)))
        }
    }


   
}
