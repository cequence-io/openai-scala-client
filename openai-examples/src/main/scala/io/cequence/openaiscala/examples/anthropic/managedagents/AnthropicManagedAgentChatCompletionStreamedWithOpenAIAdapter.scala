package io.cequence.openaiscala.examples.anthropic.managedagents

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * Streamed OpenAI-style chat completion backed by the Anthropic Managed Agents API. Managed
 * agents emit buffered `agent.message` events (not token deltas), so each chunk carries one
 * complete agent message; the final chunk carries the finish reason.
 *
 * Requires `ANTHROPIC_API_KEY` with the `managed-agents-2026-04-01` beta enabled.
 */
object AnthropicManagedAgentChatCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService =
    AnthropicServiceFactory.managedAgentAsOpenAI()

  private val messages = Seq(
    SystemMessage("You are a concise assistant."),
    UserMessage("List three interesting facts about Norway.")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_4_6)
      )
      .runWith(
        Sink.foreach { chunk =>
          chunk.choices.headOption.foreach { choice =>
            choice.delta.content.foreach(print)
            choice.finish_reason.foreach(reason => println(s"\n[finish reason: $reason]"))
          }
        }
      )
}
