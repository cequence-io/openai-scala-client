package io.cequence.openaiscala.perplexity.service.impl

import io.cequence.openaiscala.perplexity.service.PerplexityScalaClientException
import io.cequence.openaiscala.service.{ServerSentEvents => CoreServerSentEvents}
import io.cequence.wsclient.stream.{AsyncPipe, Bytes}
import play.api.libs.json.JsValue

/**
 * The shared server-sent-events decoder ([[io.cequence.openaiscala.service.ServerSentEvents]])
 * for the Agent API streams, failing with a [[PerplexityScalaClientException]].
 */
private[service] object ServerSentEvents {

  def DefaultMaxEventBytes: Int = CoreServerSentEvents.DefaultMaxEventBytes

  def jsonPayloads(maxEventBytes: Int = DefaultMaxEventBytes): AsyncPipe[Bytes, JsValue] =
    CoreServerSentEvents.jsonPayloads(maxEventBytes, new PerplexityScalaClientException(_))
}
