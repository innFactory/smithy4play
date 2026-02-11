package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class BasicSmithy4PlaySimulation extends Simulation {
  private val baseUrl = System.getProperty("baseUrl", "http://127.0.0.1:9000")

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  private val postJson = scenario("POST testWithOutput json")
    .exec(
      http("testWithOutput")
        .post("/test/thisIsAPathParam?testQuery=thisIsATestQuery")
        .header("Test-Header", "thisIsATestHeader")
        .body(StringBody("""{"message":"thisIsARequestBody"}"""))
        .asJson
        .check(status.is(200))
    )

  private val users     = Integer.getInteger("users", 10).toInt
  private val ramp      = Integer.getInteger("rampSeconds", 5).toInt
  private val steady    = Integer.getInteger("steadySeconds", 20).toInt
  private val totalUser = users

  setUp(
    postJson.inject(
      rampUsers(totalUser) during (ramp),
      constantUsersPerSec(math.max(1, totalUser / 2.0)) during (steady)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.failedRequests.count.is(0),
      global.successfulRequests.percent.is(100)
    )
}
