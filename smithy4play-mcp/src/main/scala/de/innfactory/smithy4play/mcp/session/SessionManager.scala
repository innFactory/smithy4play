package de.innfactory.smithy4play.mcp.session

import com.google.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

@Singleton
class SessionManager {
  private val sessionCounter = new AtomicInteger(0)
  private val activeSessions = new ConcurrentHashMap[String, McpSession]()

  def getOrCreateSession(sessionIdOpt: Option[String]): McpSession = {
    val sessionId = sessionIdOpt.getOrElse(generateNewSessionId())
    activeSessions.computeIfAbsent(
      sessionId,
      _ => McpSession(sessionId)
    )
  }

  def getSession(sessionId: String): Option[McpSession] =
    Option(activeSessions.get(sessionId))

  def updateSessionClientInfo(sessionId: String, clientInfo: ClientInfo): Unit = {
    activeSessions.computeIfPresent(sessionId, (_, session) => {
      session.clientInfo = Some(clientInfo)
      session
    })
  }

  def markSessionInitialized(sessionId: String): Unit = {
    activeSessions.computeIfPresent(sessionId, (_, session) => {
      session.isInitialized = true
      session
    })
  }

  def cleanupOldSessions(maxAgeMs: Long = 3600000): Unit = {
    val now = System.currentTimeMillis()
    val sessionsToRemove = activeSessions
      .entrySet()
      .stream()
      .filter(entry => (now - entry.getValue.createdAt) > maxAgeMs)
      .map(_.getKey)
      .toArray(Array.ofDim[String](_))

    sessionsToRemove.foreach { sessionId =>
      activeSessions.remove(sessionId)
    }
  }

  def getActiveSessions: List[McpSession] =
    activeSessions.values().toArray.map(_.asInstanceOf[McpSession]).toList

  private def generateNewSessionId(): String =
    s"session-${sessionCounter.incrementAndGet()}"
}

final case class McpSession(
    sessionId: String,
    var isInitialized: Boolean = false,
    var clientInfo: Option[ClientInfo] = None,
    createdAt: Long = System.currentTimeMillis()
)

final case class ClientInfo(name: String, version: String)

