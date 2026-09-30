package io.cequence.openaiscala.perplexity.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import io.cequence.openaiscala.perplexity.service.PerplexityScalaClientException
import io.cequence.openaiscala.service.{ServerSentEvents => CoreServerSentEvents}
import play.api.libs.json.JsValue

/**
 * The shared server-sent-events decoder ([[io.cequence.openaiscala.service.ServerSentEvents]])
 * for the Agent API streams, failing with a [[PerplexityScalaClientException]].
 */
private[service] object ServerSentEvents {

  val DefaultMaxEventBytes: Int = CoreServerSentEvents.DefaultMaxEventBytes

  def jsonPayloads(maxEventBytes: Int = DefaultMaxEventBytes)
    : Flow[ByteString, JsValue, NotUsed] =
    CoreServerSentEvents.jsonPayloads(maxEventBytes, new PerplexityScalaClientException(_))
}
