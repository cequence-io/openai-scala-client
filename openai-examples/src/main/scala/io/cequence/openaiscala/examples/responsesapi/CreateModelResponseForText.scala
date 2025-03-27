package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.Inputs
import io.cequence.openaiscala.examples.Example

object CreateModelResponseForText extends Example {

  override def run: Future[Unit] =
    service.createModelResponse(
      Inputs.Text("What is the capital of France?")
    ).map { response =>
      import response.usage._

      println(response.outputText.getOrElse("N/A"))
      println(inputTokens)
      println(outputTokens)
      println(totalTokens)
    }
}
