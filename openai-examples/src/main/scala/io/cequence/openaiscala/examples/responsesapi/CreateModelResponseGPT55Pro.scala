package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.{CreateModelResponseSettings, Inputs}
import io.cequence.openaiscala.examples.Example

import scala.concurrent.Future

// Smoke test: gpt-5.5-pro is Responses-API-only (chat completions endpoint rejects it).
// Confirms the model is reachable via /v1/responses.
object CreateModelResponseGPT55Pro extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Text("What is the capital of Norway? One word."),
        settings = CreateModelResponseSettings(model = ModelId.gpt_5_5_pro)
      )
      .map { response =>
        println(s"Response: ${response.outputText.getOrElse("N/A")}")
        response.usage.foreach { u =>
          println(s"Usage: in=${u.inputTokens} out=${u.outputTokens} total=${u.totalTokens}")
        }
      }
}
