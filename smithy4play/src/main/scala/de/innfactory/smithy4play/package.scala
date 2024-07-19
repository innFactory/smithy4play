package de.innfactory

import cats.MonadThrow
import cats.data.{EitherT, Kleisli}
import de.innfactory.smithy4play.routing.context.{RoutingContext, RoutingContextBase}
import org.slf4j
import play.api.Logger
import smithy4s.Blob
import smithy4s.http.{CaseInsensitive, Metadata}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros

package object smithy4play {
  // TODO Success status codes am client
  // TODO Status Code annotation in smithy file?

  case class ContentType(value: String)

  type RoutingResult[O] = EitherT[Future, Throwable, O]
  type ContextRoute[O]  = Kleisli[RoutingResult, RoutingContextBase, O]
  type EndpointRequest  = PlayHttpRequest[Blob]

  implicit def monadThrowContextRoute(implicit ec: ExecutionContext): MonadThrow[ContextRoute] =
    new MonadThrow[ContextRoute] {

      private def left[A](e: Throwable): ContextRoute[A] = Kleisli { ctx =>
        EitherT.leftT[Future, A](e)
      }

      private def right[A](a: A): ContextRoute[A] = Kleisli { ctx =>
        EitherT.rightT[Future, Throwable](a)
      }

      private def rightEither[A](a: A): RoutingResult[A] =
        EitherT.rightT[Future, Throwable](a)

      override def raiseError[A](e: Throwable): ContextRoute[A] = left(e)

      override def handleErrorWith[A](
        fa: ContextRoute[A]
      )(f: Throwable => ContextRoute[A]): ContextRoute[A] = Kleisli { ctx =>
        fa(ctx).biflatMap(v => f(v)(ctx), rightEither)
      }

      override def pure[A](x: A): ContextRoute[A] = right(x)

      override def flatMap[A, B](fa: ContextRoute[A])(f: A => ContextRoute[B]): ContextRoute[B] =
        fa.flatMap(f)

      override def tailRecM[A, B](a: A)(f: A => ContextRoute[Either[A, B]]): ContextRoute[B] =
        f(a).flatMap {
          case Left(value)  => tailRecM(value)(f)
          case Right(value) => Kleisli(ctx => EitherT.rightT[Future, Throwable](value))
        }
    }

  case class PlayHttpRequest[Body](body: Body, metadata: Metadata) {
    def addHeaders(headers: Map[CaseInsensitive, Seq[String]]): PlayHttpRequest[Body] = this.copy(
      metadata = metadata.copy(
        headers = metadata.headers ++ headers
      )
    )
  }

  private[smithy4play] val logger: slf4j.Logger = Logger("smithy4play").logger

  private[smithy4play] trait Showable {
    this: Product =>
    override def toString: String = this.show
  }

  private[smithy4play] object Showable {
    implicit class ShowableProduct(product: Product) {
      def show: String = {
        val className = product.productPrefix
        val fieldNames = product.productElementNames.toList
        val fieldValues = product.productIterator.toList
        val fields = fieldNames.zip(fieldValues).map { case (name, value) =>
          value match {
            case subProduct: Product => s"$name = ${subProduct.show}"
            case _ => s"$name = $value"
          }
        }
        fields.mkString(s"$className(", ", ", ")")
      }
    }
  }

}
