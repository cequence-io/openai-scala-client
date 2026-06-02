package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Live regression check for the xhigh reasoning-effort branch (the code path shared by Opus
 * 4.7 and the new Opus 4.8). Runs a short real completion against Opus 4.7 with
 * reasoning_effort=xhigh so OutputEffort.xhigh + adaptive thinking is actually sent to
 * Anthropic and accepted. Requires ANTHROPIC_API_KEY. (Opus 4.8 follows the identical mapping
 * but isn't live on the API yet.)
 */
object AnthropicOpus47XHighReasoningLive extends ExampleBase[OpenAIChatCompletionService] {

  override protected val service: OpenAIChatCompletionService =
    AnthropicServiceFactory.asOpenAI()

  private val messages = Seq(
    SystemMessage("You are a concise assistant."),
    UserMessage("In one sentence: why is the sky blue?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages,
        CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_opus_4_7,
          max_tokens = Some(4000),
          reasoning_effort = Some(ReasoningEffort.xhigh)
        )
      )
      .map { response =>
        println("Response:")
        println(response.contentHead)
        println(s"\nUsage: ${response.usage}")
      }
}
