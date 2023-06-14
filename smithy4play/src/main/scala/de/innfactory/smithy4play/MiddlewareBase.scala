package de.innfactory.smithy4play

import cats.data.{EitherT, Kleisli}
import play.api.Logger
import smithy4s.ShapeTag

import scala.concurrent.{ExecutionContext, Future}

trait MiddlewareBase {

  val logger: Logger = Logger("smithy4play")

  val middlewareEnableHint: Option[ShapeTag[_]]  = None
  val middlewareDisableFlag: Option[ShapeTag[_]] = None

  protected def enableLogic(f: RoutingContext => RoutingContext)(implicit
    executionContext: ExecutionContext
  ): Kleisli[RouteResult, RoutingContext, RoutingContext] =
    Kleisli[RouteResult, RoutingContext, RoutingContext] { r =>
      middlewareEnableHint match {
        case Some(value) =>
          r.serviceHints.get(value) match {
            case Some(_) => EitherT.right[ContextRouteError](Future(f(r)))
            case None    => EitherT.right(Future(r))
          }
        case None        =>
          EitherT.right[ContextRouteError](Future(r))
      }
    }

  protected def combinedLogic(
    f: RoutingContext => RoutingContext
  )(implicit executionContext: ExecutionContext): Kleisli[RouteResult, RoutingContext, RoutingContext] =
    Kleisli[RouteResult, RoutingContext, RoutingContext] { r =>
      middlewareEnableHint match {
        case Some(value) =>
          r.serviceHints.get(value) match {
            case Some(_) => disableFlagMatcher(f, r)
            case None    => EitherT.right(Future(r))
          }
        case None        =>
          EitherT.right[ContextRouteError](Future(r))
      }
    }

  protected def disableLogic(
    f: RoutingContext => RoutingContext
  )(implicit executionContext: ExecutionContext): Kleisli[RouteResult, RoutingContext, RoutingContext] =
    Kleisli[RouteResult, RoutingContext, RoutingContext] { r =>
      disableFlagMatcher(f, r)
    }

  private def disableFlagMatcher(f: RoutingContext => RoutingContext, r: RoutingContext)(implicit
    executionContext: ExecutionContext
  ): RouteResult[RoutingContext] =
    middlewareDisableFlag match {
      case Some(value) =>
        r.endpointHints.get(value) match {
          case Some(_) => EitherT.right(Future(r))
          case None    => EitherT.right[ContextRouteError](Future(f(r)))
        }
      case None        =>
        EitherT.right[ContextRouteError](Future(f(r)))
    }

  def logic: Kleisli[RouteResult, RoutingContext, RoutingContext]

}
