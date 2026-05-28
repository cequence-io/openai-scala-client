package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and 'AWS_BEDROCK_ACCESS_KEY', 'AWS_BEDROCK_SECRET_KEY', 'AWS_BEDROCK_REGION' environment variables to be set
object AnthropicBedrockCreateChatCompletionWithReasoningAndOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService =
    ChatCompletionProvider.anthropicBedrock(ec, materializer)

  // Sonnet 4.6 supports adaptive thinking via `output_config.effort` (mapped from reasoning_effort).
  // Older models (Sonnet 4.5, Opus 4.5, etc.) use the legacy `thinking.budget_tokens` path -
  // make sure `max_tokens > thinking.budget_tokens` in that case.
  private val modelId = "eu." + NonOpenAIModelId.bedrock_claude_sonnet_4_6

  private val messages = Seq(
    SystemMessage("You are a careful problem solver."),
    UserMessage(
      "A bat and a ball cost 1.10 in total. The bat costs 1.00 more than the ball. " +
        "How much does the ball cost? Think step by step, then give the final answer."
    )
  )

  private val efforts: Seq[ReasoningEffort] =
    Seq(ReasoningEffort.low, ReasoningEffort.medium, ReasoningEffort.high)

  override protected def run: Future[_] = {
    // Run sequentially (not in parallel) so the printed output stays grouped per effort level.
    efforts.foldLeft(Future.successful(())) {
      (
        acc,
        effort
      ) =>
        acc.flatMap { _ =>
          println(s"\n===== reasoning_effort = $effort =====")
          service
            .createChatCompletion(
              messages = messages,
              settings = CreateChatCompletionSettings(
                model = modelId,
                reasoning_effort = Some(effort),
                max_tokens = Some(8000)
              )
            )
            .map { response =>
              val usage = response.usage
              val reasoningTokens =
                usage.flatMap(_.completion_tokens_details.flatMap(_.reasoning_tokens))
              val completionTokens = usage.map(_.completion_tokens)
              val totalTokens = usage.map(_.total_tokens)
              println(response.contentHead)
              println(
                s"\n[usage] reasoning_tokens=$reasoningTokens " +
                  s"completion_tokens=$completionTokens total_tokens=$totalTokens"
              )
            }
        }
    }
  }
}
