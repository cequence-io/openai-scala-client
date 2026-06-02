package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.anthropic.service.impl.toAnthropicSettings
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Network-free verification that Claude Opus 4.8 is wired into the Anthropic reasoning-effort
 * handling: it should be treated as an output_config.effort (adaptive thinking) model, and
 * `xhigh` should map to OutputEffort.xhigh (not downgraded). We exercise the pure
 * `toAnthropicSettings` mapping for both the direct API id and the Bedrock id and print the
 * resulting `thinking` / `output_config`. No remote call is made, so this passes even before
 * the model is live on the API.
 */
object AnthropicOpus48ReasoningEffortMapping extends ExampleBase[OpenAIChatCompletionService] {

  // service is required by ExampleBase but never invoked here
  override protected val service: OpenAIChatCompletionService =
    AnthropicServiceFactory.asOpenAI()

  private val efforts = Seq(
    ReasoningEffort.none,
    ReasoningEffort.minimal,
    ReasoningEffort.low,
    ReasoningEffort.medium,
    ReasoningEffort.high,
    ReasoningEffort.xhigh
  )

  private def report(model: String): Unit = {
    println(s"\n=== model: $model ===")
    efforts.foreach { effort =>
      val anthropic = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = model,
          max_tokens = Some(10000),
          temperature = Some(0.2),
          reasoning_effort = Some(effort)
        )
      )
      println(
        f"reasoning_effort=${effort.toString}%-8s -> thinking=${anthropic.thinking}%-40s " +
          s"output_config=${anthropic.output_config}, temperature=${anthropic.temperature}"
      )
    }
  }

  override protected def run: Future[_] = Future {
    report(NonOpenAIModelId.claude_opus_4_8)
    report(NonOpenAIModelId.bedrock_claude_opus_4_8)

    // sanity: an older non-output-effort model still uses the legacy thinking-budget path
    report(NonOpenAIModelId.claude_3_7_sonnet_latest)
  }
}
