package controllers

import cats.data.EitherT
import cats.data.Kleisli
import de.innfactory.smithy4play.{AutoRouting, ContextRoute, RoutingContext}
import play.api.mvc.ControllerComponents
import testDefinitions.test._
import smithy4s.Timestamp

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
@AutoRouting
class MCPExampleController @Inject()(implicit
    cc: ControllerComponents,
    ec: ExecutionContext
) extends MCPExampleControllerService[ContextRoute] {

  // Mock data store
  private val mockCustomers = scala.collection.mutable.Map[String, Customer](
    "1" -> Customer(
      id = "1",
      name = "John Doe",
      email = "john@example.com",
      active = Some(true),
      tags = Some(List("vip", "premium")),
      createdAt = Some(Timestamp.nowUTC()),
      updatedAt = Some(Timestamp.nowUTC())
    ),
    "2" -> Customer(
      id = "2", 
      name = "Jane Smith",
      email = "jane@example.com",
      active = Some(true),
      tags = Some(List("regular")),
      createdAt = Some(Timestamp.nowUTC()),
      updatedAt = Some(Timestamp.nowUTC())
    )
  )

  override def listCustomers(limit: Int, cursor: Option[String], query: Option[String]): ContextRoute[ListCustomersOutput] =
    Kleisli { rc =>
      // Check for auth headers to validate bearer token functionality
      val authHeader = rc.requestHeader.headers.get("Authorization")
      if (authHeader.isEmpty) {
        EitherT.leftT[Future, ListCustomersOutput](
          de.innfactory.smithy4play.Smithy4PlayError(
            "Missing Authorization header",
            de.innfactory.smithy4play.Status(Map.empty, 401),
            contentType = "application/json"
          )
        )
      } else {
        val customers = mockCustomers.values.toList
        val filteredCustomers = query match {
          case Some(q) => customers.filter(c => c.name.toLowerCase.contains(q.toLowerCase) || c.email.toLowerCase.contains(q.toLowerCase))
          case None => customers
        }
        
        val actualLimit = limit
        val paginatedCustomers = filteredCustomers.take(actualLimit)
        
        val response = ListCustomersOutput(
          body = PaginatedCustomers(
            customers = paginatedCustomers,
            total = filteredCustomers.length,
            nextCursor = if (paginatedCustomers.length >= actualLimit) Some("next-page") else None
          )
        )
        EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](response)
      }
    }

  override def getCustomer(customerId: String): ContextRoute[GetCustomerOutput] =
    Kleisli { rc =>
      mockCustomers.get(customerId) match {
        case Some(customer) =>
          val response = GetCustomerOutput(body = customer)
          EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](response)
        case None =>
          EitherT.leftT[Future, GetCustomerOutput](
            de.innfactory.smithy4play.Smithy4PlayError(
              s"Customer with ID $customerId not found",
              de.innfactory.smithy4play.Status(Map.empty, 404),
              contentType = "application/json"
            )
          )
      }
    }

  override def createCustomer(body: CustomerCreateDto): ContextRoute[CreateCustomerOutput] =
    Kleisli { rc =>
      val newId = java.util.UUID.randomUUID().toString
      val now = Timestamp.nowUTC()
      val newCustomer = Customer(
        id = newId,
        name = body.name,
        email = body.email,
        active = Some(body.active),
        tags = body.tags,
        createdAt = Some(now),
        updatedAt = Some(now)
      )
      
      mockCustomers += newId -> newCustomer
      val response = CreateCustomerOutput(body = newCustomer)
      EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](response)
    }

  override def updateCustomer(customerId: String, body: CustomerUpdateDto): ContextRoute[UpdateCustomerOutput] =
    Kleisli { rc =>
      mockCustomers.get(customerId) match {
        case Some(existing) =>
          val updated = existing.copy(
            name = body.name.getOrElse(existing.name),
            email = body.email.getOrElse(existing.email),
            active = body.active.orElse(existing.active),
            tags = body.tags.orElse(existing.tags),
            updatedAt = Some(Timestamp.nowUTC())
          )
          mockCustomers += customerId -> updated
          val response = UpdateCustomerOutput(body = updated)
          EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](response)
        case None =>
          EitherT.leftT[Future, UpdateCustomerOutput](
            de.innfactory.smithy4play.Smithy4PlayError(
              s"Customer with ID $customerId not found",
              de.innfactory.smithy4play.Status(Map.empty, 404),
              contentType = "application/json"
            )
          )
      }
    }

  override def deleteCustomer(customerId: String): ContextRoute[Unit] =
    Kleisli { rc =>
      mockCustomers.remove(customerId) match {
        case Some(_) =>
          EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](())
        case None =>
          EitherT.leftT[Future, Unit](
            de.innfactory.smithy4play.Smithy4PlayError(
              s"Customer with ID $customerId not found",
              de.innfactory.smithy4play.Status(Map.empty, 404),
              contentType = "application/json"
            )
          )
      }
    }

  override def searchCustomers(
    limit: Int,
    cursor: Option[String],
    name: Option[String],
    email: Option[String], 
    active: Option[Boolean],
    tags: Option[List[String]]
  ): ContextRoute[SearchCustomersOutput] =
    Kleisli { rc =>
      var customers = mockCustomers.values.toList
      
      // Apply filters
      name.foreach(n => customers = customers.filter(_.name.toLowerCase.contains(n.toLowerCase)))
      email.foreach(e => customers = customers.filter(_.email.toLowerCase.contains(e.toLowerCase)))
      active.foreach(a => customers = customers.filter(_.active.contains(a)))
      tags.foreach { tagList =>
        customers = customers.filter { customer =>
          customer.tags.exists(customerTags => tagList.exists(customerTags.contains))
        }
      }
      
      val actualLimit = limit
      val paginatedCustomers = customers.take(actualLimit)
      
      val response = SearchCustomersOutput(
        body = PaginatedCustomers(
          customers = paginatedCustomers,
          total = customers.length,
          nextCursor = if (paginatedCustomers.length >= actualLimit) Some("next-page") else None
        )
      )
      EitherT.rightT[Future, de.innfactory.smithy4play.ContextRouteError](response)
    }
}