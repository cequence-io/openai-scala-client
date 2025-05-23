package io.cequence.openaiscala.examples.googlegemini

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.Content
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiGenerateContentStreamed extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  private val systemPrompt: Content =
    Content.textPart("You are a helpful assistant who knows elfs personally.", User)

  private val contents: Seq[Content] = Seq(
    Content.textPart("What is the weather like in Norway?", User)
  )

  override protected def run: Future[_] =
    service
      .generateContentStreamed(
        contents,
        settings = GenerateContentSettings(
          model = NonOpenAIModelId.gemini_2_0_flash_exp,
          systemInstruction = Some(systemPrompt),
          generationConfig = Some(
            GenerationConfig(
              maxOutputTokens = Some(5000),
              temperature = Some(0.2)
            )
          )
        )
      )
      .runWith(
        Sink.foreach { response =>
          print(response.contentHeadText)
        }
      )
}
