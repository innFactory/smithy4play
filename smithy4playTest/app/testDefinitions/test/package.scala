package testDefinitions

package object test {
  type TestControllerService[F[_]] = smithy4s.kinds.FunctorAlgebra[TestControllerServiceGen, F]
  val TestControllerService = TestControllerServiceGen

}
