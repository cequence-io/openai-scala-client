package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{Inputs, CreateModelResponseSettings}
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.domain.ModelId

object CancelModelResponse extends Example {

  override def run: Future[Unit] =
    for {
      response <- service.createModelResponse(
        Inputs.Text("Write a very long story about the history of artificial intelligence."),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_5_2,
          background = Some(true)
        )
      )

      _ = {
        println(s"Created model response with ID: ${response.id}")
        println(s"Initial status: ${response.status}")
        println(s"Background: ${response.background}")
        println(s"\nCancelling response ${response.id}...")
      }

      cancelledResponse <- service.cancelModelResponse(response.id)
    } yield {
      println(s"\nCancelled response:")
      println(s"ID: ${cancelledResponse.id}")
      println(s"Status: ${cancelledResponse.status}")
      println(s"Background: ${cancelledResponse.background}")
    }
}
