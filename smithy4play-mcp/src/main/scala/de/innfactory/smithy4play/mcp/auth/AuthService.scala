package de.innfactory.smithy4play.mcp.auth

import cats.data.EitherT

import scala.concurrent.{ExecutionContext, Future}

trait AuthService {

  def verifyAwsToken(token: JWToken)(using ExecutionContext): EitherT[Future, AuthError, TokenClaims]
}

final case class JWToken(value: String)

final case class TokenClaims(subject: String, audience: Option[String] = None)

sealed trait AuthError {
  def getMsg: String
}

object AuthError {
  final case class InvalidToken(reason: String) extends AuthError {
    override def getMsg: String = s"Invalid token: $reason"
  }

  final case class TokenExpired(reason: String) extends AuthError {
    override def getMsg: String = s"Token expired: $reason"
  }

  final case class UnauthorizedAccess(reason: String) extends AuthError {
    override def getMsg: String = s"Unauthorized: $reason"
  }
}

