package controller.models

import de.innfactory.smithy4play.client.SmithyPlayClient
import de.innfactory.smithy4play.{ContextRoute }
import de.innfactory.smithy4play.client.RunnableClientRequest
import de.innfactory.smithy4play.client.ClientResponse
import play.api.libs.json.{JsValue, Json, OFormat}
import smithy4s.Service
import smithy4s.http.HttpResponse
import smithy4s.kinds.{Kind1, PolyFunction5}
import testDefinitions.test.{SimpleTestResponse, TestControllerServiceGen}

case class TestError(
  message: String,
) extends Throwable 

