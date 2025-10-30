package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.ModelId

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{GetInputTokensCountSettings, Input, Inputs}
import io.cequence.openaiscala.examples.Example

object GetModelResponseInputTokenCountsForMessages extends Example {

  override def run: Future[Unit] = {
    val settings = GetInputTokensCountSettings(
      model = Some(ModelId.gpt_5_mini),
      instructions = Some("You are a helpful assistant specialized in geography.")
    )

    service
      .getModelResponseInputTokenCounts(
        Inputs.Items(
          Input.ofInputSystemTextMessage(
            "You are a helpful assistant. Be verbose and detailed."
          ),
          Input.ofInputUserTextMessage(
            "What is the capital of France and what are some famous landmarks there?"
          ),
          Input.ofInputUserTextMessage(
            "Tell me more about the Eiffel Tower."
          )
        ),
        settings
      )
      .map { result =>
        println(s"Object type: ${result.`object`}")
        println(s"Input tokens for this conversation: ${result.inputTokens}")
      }
  }
}
