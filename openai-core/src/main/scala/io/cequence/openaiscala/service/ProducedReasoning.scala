package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.{BaseMessage, ReasoningBlock, ReasoningMessage}
import io.cequence.wsclient.domain.SiteBinding
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * The reasoning a service returned - its most recent blocks, the oldest dropped - and so the
 * only reasoning its adapter passes back. Reasoning is bound to the provider and account that
 * made it (live 2026-10-26: OpenAI's encrypted reasoning is a 400 on Bedrock, Bedrock's a 400
 * on OpenAI), so a carried [[ReasoningMessage]] that reaches another service (a failover, a
 * router) is left out there. One per service instance ([[ProducedReasoning.of]]; a wrapper
 * that is a [[ProducedReasoning.Delegate]] shares the wrapped service's), shared by the
 * service's sync and streamed adapters.
 */
private[openaiscala] final class ProducedReasoning(maxEntries: Int = 16384) {

  private val logger = LoggerFactory.getLogger(getClass)

  private val keys =
    new java.util.LinkedHashMap[String, java.lang.Boolean](64, 0.75f, true) {
      override def removeEldestEntry(
        eldest: java.util.Map.Entry[String, java.lang.Boolean]
      ): Boolean = size() > maxEntries
    }

  def remember(reasoning: Option[ReasoningMessage]): Unit =
    reasoning.foreach(_.blocks.foreach(remember))

  def remember(block: ReasoningBlock): Unit = {
    val key = ProducedReasoning.key(block)
    keys.synchronized {
      keys.put(key, java.lang.Boolean.TRUE)
      ()
    }
  }

  // a hit counts as recent (access order)
  def contains(block: ReasoningBlock): Boolean = {
    val key = ProducedReasoning.key(block)
    keys.synchronized {
      keys.get(key) != null
    }
  }

  /**
   * The messages with every carried [[ReasoningMessage]] cut to the blocks this service
   * returned that `sendable` accepts (e.g. the block kinds its provider takes back); a message
   * left without blocks is dropped.
   */
  def carried(
    messages: Seq[BaseMessage],
    serviceName: String
  )(
    sendable: ReasoningBlock => Boolean
  ): Seq[BaseMessage] =
    messages.flatMap {
      case ReasoningMessage(blocks) =>
        val own = blocks.filter(contains)
        val kept = own.filter(sendable)

        if (own.size < blocks.size)
          logger.debug(
            s"$serviceName: ${blocks.size - own.size} carried reasoning block(s) not returned by this service - not sent."
          )
        if (kept.size < own.size)
          logger.debug(
            s"$serviceName: ${own.size - kept.size} carried reasoning block(s) the request cannot take back - not sent."
          )

        if (kept.nonEmpty)
          Some(ReasoningMessage(kept))
        else
          None

      case message => Some(message)
    }
}

private[openaiscala] object ProducedReasoning {

  /**
   * A service whose reasoning is another service's: a wrapper that hands its calls to the
   * service it wraps - the merged streamed OpenAI service, whose sync calls the wrapped
   * service serves (and whose Responses adapter remembers their reasoning). An adapter built
   * over the wrapper - its typed streams' Responses adapter - then shares the wrapped
   * service's registry.
   */
  trait Delegate {
    def producedReasoningOwner: AnyRef
  }

  private val byService = new java.util.WeakHashMap[AnyRef, ProducedReasoning]()

  /** The reasoning the given (underlying) service returned - its owner's, for a delegate. */
  def of(service: AnyRef): ProducedReasoning =
    service match {
      case delegate: Delegate if !(delegate.producedReasoningOwner eq delegate) =>
        of(delegate.producedReasoningOwner)

      case _ =>
        byService.synchronized {
          Option(byService.get(service)).getOrElse {
            val produced = new ProducedReasoning()
            byService.put(service, produced)
            produced
          }
        }
    }

  private val bySite =
    new java.util.LinkedHashMap[String, ProducedReasoning](16, 0.75f, true) {
      override def removeEldestEntry(
        eldest: java.util.Map.Entry[String, ProducedReasoning]
      ): Boolean = size() > 256
    }

  /**
   * The reasoning returned by a site - a base URL and its credentials, i.e. a provider account
   *   - for the services that share one (e.g. a service's sync and streamed implementations).
   */
  def forSite(site: SiteBinding): ProducedReasoning = {
    val account = site.requestContextFun match {
      case Some(contextFun) => s"fun:${System.identityHashCode(contextFun)}"
      case None =>
        digest(site.requestContext.authHeaders.map { case (k, v) => s"$k=$v" }.mkString("\n"))
    }
    val key = s"${site.coreUrl} $account"
    bySite.synchronized {
      Option(bySite.get(key)).getOrElse {
        val produced = new ProducedReasoning()
        bySite.put(key, produced)
        produced
      }
    }
  }

  // a block's identity: its id (a Responses API item), else a digest of what makes it unique -
  // its signature or encrypted reasoning, else its text
  private def key(block: ReasoningBlock): String =
    block.id match {
      case Some(id) => s"id:$id"
      case None =>
        digest(
          block.signature
            .orElse(block.encrypted)
            .orElse(block.text)
            .getOrElse(block.summary.mkString("\n"))
        )
    }

  private def digest(value: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map("%02x".format(_))
      .mkString
}
