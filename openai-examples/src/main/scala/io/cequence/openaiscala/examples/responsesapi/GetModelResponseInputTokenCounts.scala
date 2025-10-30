package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.ModelId

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{GetInputTokensCountSettings, Inputs}
import io.cequence.openaiscala.examples.Example

object GetModelResponseInputTokenCounts extends Example {

  override def run: Future[Unit] = {
    val settings = GetInputTokensCountSettings(
      model = Some(ModelId.gpt_4o)
    )

    service
      .getModelResponseInputTokenCounts(
        Inputs.Text("What is the capital of France and what are some famous landmarks there?"),
        settings
      )
      .map { result =>
        println(s"Object type: ${result.`object`}")
        println(s"Input tokens: ${result.inputTokens}")
      }
  }
}
