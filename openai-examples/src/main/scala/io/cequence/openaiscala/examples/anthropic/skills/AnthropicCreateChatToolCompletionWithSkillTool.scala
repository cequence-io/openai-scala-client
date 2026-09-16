package io.cequence.openaiscala.examples.anthropic.skills

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.ChatCompletionTool.{SkillSource, SkillTool}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.examples.{ChatChunkPrinter, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.Future

/**
 * Anthropic's built-in `pptx` skill through the OpenAI-style adapter with the provider-neutral
 * `SkillTool`: the adapter loads the skill into the code-execution container (and adds the
 * code execution tool), so the model builds the presentation server-side. On the typed stream
 * the container work shows up as server-side `code_execution` / `bash_code_execution` tool
 * calls and results in front of the answer; the generated file ids ride on the result blocks.
 *
 * Requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY`.
 */
object AnthropicCreateChatToolCompletionWithSkillTool
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  // building a presentation takes a while - give the request several minutes
  private val timeout = 10 * 60 * 1000

  override protected val service: OpenAIChatCompletionStreamedService =
    AnthropicServiceFactory.asOpenAI(
      timeouts = Some(Timeouts(requestTimeout = Some(timeout), readTimeout = Some(timeout)))
    )

  override protected def run: Future[_] =
    service
      .createChatToolCompletionStreamed(
        messages = Seq(
          UserMessage("Create a short two-slide presentation about renewable energy.")
        ),
        tools =
          Seq(SkillTool("pptx", version = Some("latest"), source = SkillSource.Provider)),
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_4_5_20250929,
          max_tokens = Some(8000)
        )
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
}
