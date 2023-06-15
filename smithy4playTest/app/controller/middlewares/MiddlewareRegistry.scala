package controller.middlewares

import de.innfactory.smithy4play.middleware.{ MiddlewareBase, MiddlewareRegistryBase }

import javax.inject.Inject

class MiddlewareRegistry @Inject() (
  disableAbleMiddleware: DisableAbleMiddleware,
  testMiddlewareImpl: TestMiddlewareImpl
) extends MiddlewareRegistryBase {
  override val middlewares: Seq[MiddlewareBase] = Seq(disableAbleMiddleware, testMiddlewareImpl)
}
