package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * OpenAI-style chat completion backed by the Anthropic Managed Agents API. The adapter lazily
 * creates (and caches) an agent for the requested model + system prompt, reuses a shared cloud
 * environment, runs each call as a one-turn session, and deletes the session afterwards - so
 * managed agents plug into the standard OpenAI-style workflows (routers, retries, ...).
 *
 * Requires `ANTHROPIC_API_KEY` with the `managed-agents-2026-04-01` beta enabled. Note that a
 * managed-agent turn provisions a container and can take noticeably longer than a plain chat
 * completion.
 */
object AnthropicManagedAgentChatCompletionWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService =
    AnthropicServiceFactory.managedAgentAsOpenAI()

  private val messages = Seq(
    SystemMessage("You are a concise assistant."),
    UserMessage("List three interesting facts about Norway.")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_4_6)
      )
      .map { response =>
        println(response.contentHead)
        println(s"usage: ${response.usage}")
      }
}
