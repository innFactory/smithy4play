import play.api.mvc.RequestHeader
import smithy4s.http.CaseInsensitive

package object play4s {

  def getHeaders(req: RequestHeader) =
    req.headers.headers.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

}
