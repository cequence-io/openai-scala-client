package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ReasoningConfig
}
import io.cequence.openaiscala.domain.settings.ReasoningEffort
import io.cequence.openaiscala.examples.Example

import scala.concurrent.Future

/**
 * Smoke test: GPT-5.6's top reasoning tier 'max' is Responses-API-only - the chat completions
 * endpoint rejects reasoning_effort=max (and 'minimal') for gpt-5.6 models, while
 * /v1/responses accepts it (live-verified 2026-07-11). On the chat-completion path,
 * ChatCompletionSettingsConversions.gpt5_6 downgrades 'max' to 'xhigh' instead.
 */
object CreateModelResponseGPT56SolMaxReasoning extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Text("What is the capital of Norway? One word."),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_5_6_sol,
          reasoning = Some(ReasoningConfig(effort = Some(ReasoningEffort.max)))
        )
      )
      .map { response =>
        println(s"Response: ${response.outputText.getOrElse("N/A")}")
        response.usage.foreach { u =>
          println(s"Usage: in=${u.inputTokens} out=${u.outputTokens} total=${u.totalTokens}")
        }
      }
}
