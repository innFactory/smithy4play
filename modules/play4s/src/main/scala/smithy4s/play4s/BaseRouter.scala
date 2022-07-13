package main.scala.smithy4s.play4s

import main.scala.smithy4s.play4s.MyMonads.ContextRoute
import play.api.mvc.{ControllerComponents, Handler, RequestHeader}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import smithy4s.Monadic

import scala.concurrent.ExecutionContext

abstract class BaseRouter(implicit
    cc: ControllerComponents,
    executionContext: ExecutionContext
) extends SimpleRouter {

  implicit def transformToRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
      _
  ] <: ContextRoute[_]](
      impl: Monadic[Alg, F]
  )(implicit serviceProvider: smithy4s.Service.Provider[Alg, Op]): Routes = {
    new SmithyPlayRouter[Alg, Op, F](impl).routes()
  }

  def chain(
      toChain: Seq[Routes]
  ): PartialFunction[RequestHeader, Handler] =
    toChain.foldLeft(PartialFunction.empty[RequestHeader, Handler])((a, b) =>
      a orElse b
    )

  val controllers: Seq[Routes]

  def chainedRoutes: Routes = chain(controllers)

  override def routes: Routes = chainedRoutes

  // TODO: Adding access to swagger files to routes
  /*val docs: Routes = new PartialFunction[RequestHeader, Handler] {
    override def isDefinedAt(x: RequestHeader): Boolean = {
      println(x.path)
      x.path.equals("/swagger") || x.path.equals("/swagger/list")
    }

    override def apply(v1: RequestHeader): Handler = Action.async {
      implicit request =>
        request.path match {
          case "/swagger/list"  => Future(Ok("list"))
        }

        val path =
          "./target/scala-2.13/resource_managed/main/playSmithy.HomeControllerService.json"
        val file = new File(path)
        val stream = new FileInputStream(file)
        val json =
          try { Json.parse(stream) }
          finally { stream.close() }

        val path1 =
          "./target/scala-2.13/resource_managed/main/playSmithy.PizzaAdminService.json"
        val file1 = new File(path1)
        val stream1 = new FileInputStream(file1)
        val json1 =
          try { Json.parse(stream1) }
          finally { stream1.close() }

        Future(
          Ok(
            Json.toJson(
              json
            )
          )
        )
    }
  }
   */

}
