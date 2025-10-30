package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.Inputs
import io.cequence.openaiscala.domain.responsesapi.Input
import io.cequence.openaiscala.examples.Example

object CreateModelResponseForMessages extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Items(
          Input.ofInputSystemTextMessage(
            "You are a helpful assistant. Be verbose and detailed and don't be afraid to use emojis."
          ),
          Input.ofInputUserTextMessage(
            "What is the capital of France?"
          )
        )
      )
      .map { response =>
        println(response.outputText.getOrElse("N/A"))
        response.usage.foreach { usage =>
          println(usage.inputTokens)
          println(usage.outputTokens)
          println(usage.totalTokens)
        }
      }
}
