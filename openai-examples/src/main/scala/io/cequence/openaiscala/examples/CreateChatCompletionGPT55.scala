package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}

import scala.concurrent.Future

// Smoke test for GPT-5.5 SettingsConversion. We deliberately set every field that GPT-5.5
// rejects at the API boundary (verified against OpenAI: temperature/top_p/presence_penalty/
// frequency_penalty/max_tokens/logprobs all return 400). If our conversion is wired correctly,
// the wrapper should silently coerce them and the call should succeed.
object CreateChatCompletionGPT55 extends Example {

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = Seq(
          SystemMessage("You are a helpful assistant."),
          UserMessage("What is the capital of Norway? One word.")
        ),
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_5_5,
          // All of these are illegal on gpt-5.5 raw; conversion must coerce/strip them:
          max_tokens = Some(2000), // → max_completion_tokens
          temperature = Some(0.5), // → 1
          top_p = Some(0.5), // → 1
          presence_penalty = Some(0.5), // → 0
          frequency_penalty = Some(0.5), // → 0
          logprobs = Some(true), // → None
          reasoning_effort = Some(ReasoningEffort.low)
        )
      )
      .map { response =>
        println(s"Response: ${response.contentHead}")
        println(s"Usage: ${response.usage.get}")
      }
}
