package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

// requires `openai-scala-google-gemini-client` as a dependency and `GEMINI_API_KEY` environment variable to be set
object GoogleGeminiListModels extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  override protected def run: Future[_] =
    service.listModels(pageSize = Some(100)).map { modelsResponse =>
      println(
        "Models: \n" + modelsResponse.models
          .map(model => s"${model.name} - ${model.supportedGenerationMethods.mkString(", ")}")
          .mkString("\n")
      )
    }
}
