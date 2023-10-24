package controller.middlewares

import de.innfactory.smithy4play.middleware.{ MiddlewareBase, MiddlewareRegistryBase, ValidateAuthMiddleware }

import javax.inject.Inject

class MiddlewareRegistry @Inject() (
  disableAbleMiddleware: DisableAbleMiddleware,
  testMiddlewareImpl: TestMiddlewareImpl,
  validateAuthMiddleware: ValidateAuthMiddleware,
  changeStatusCodeMiddleware: ChangeStatusCodeMiddleware
) extends MiddlewareRegistryBase {
  override val middlewares: Seq[MiddlewareBase] =
    Seq(disableAbleMiddleware, testMiddlewareImpl, validateAuthMiddleware, changeStatusCodeMiddleware)
}
