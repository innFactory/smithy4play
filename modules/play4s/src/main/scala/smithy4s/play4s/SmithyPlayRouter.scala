package play4s
import play.api.mvc.{ControllerComponents, Handler, RequestHeader}
import play.api.routing.Router.Routes
import play4s.MyMonads.ContextRoute
import smithy4s.http.HttpEndpoint
import smithy4s.{GenLift, HintMask, Monadic}
import smithy4s.internals.InputOutput

import scala.concurrent.ExecutionContext

class SmithyPlayRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[
  _
] <: ContextRoute[_]](
                  impl: Monadic[Alg, F],
                )(implicit cc: ControllerComponents, ec: ExecutionContext) {

  def routes()(implicit
               serviceProvider: smithy4s.Service.Provider[Alg, Op]
  ): Routes = {
    println("[SmithyPlayRouter]")

    val service = serviceProvider.service
    val interpreter = service.asTransformation[GenLift[F]#λ](impl)
    val endpoints = service.endpoints

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(x: RequestHeader): Boolean = {
        println("[SmithyPlayRouter] isDefinedAt" + x.path)
        endpoints.exists(ep => {
          val res = HttpEndpoint
            .cast(ep)
            .get
            .matches(x.path.replaceFirst("/", "").split("/"))
          res.isDefined && x.method.equals(
            HttpEndpoint
              .cast(ep)
              .get
              .method
              .showUppercase
          )
        })
      }

      override def apply(v1: RequestHeader): Handler = {
        println("[SmithyPlayRouter] apply")

        val ep = endpoints
          .filter(ep =>
            HttpEndpoint
              .cast(ep)
              .get
              .matches(v1.path.replaceFirst("/", "").split("/"))
              .isDefined && HttpEndpoint
              .cast(ep)
              .get
              .method
              .showUppercase
              .equals(v1.method)
          )
          .head
        new SmithyPlayEndpoint(
          interpreter,
          ep,
          smithy4s.http.json.codecs(
            smithy4s.api.SimpleRestJson.protocol.hintMask ++ HintMask(
              InputOutput
            )
          )
        ).handler(v1)
      }

    }
  }
}
