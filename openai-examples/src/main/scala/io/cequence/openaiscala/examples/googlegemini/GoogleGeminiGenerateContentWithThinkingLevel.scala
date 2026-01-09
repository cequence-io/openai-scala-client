package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.{Content, ThinkingLevel}
import io.cequence.openaiscala.gemini.domain.settings.{GenerateContentSettings, GenerationConfig, ThinkingConfig}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiGenerateContentWithThinkingLevel extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  private val systemPrompt: Content =
    Content.textPart("You are a helpful assistant who knows elfs personally.", User)

  private val contents: Seq[Content] = Seq(
    Content.textPart("What is the weather like in Norway?", User)
  )

  override protected def run: Future[_] =
    service
      .generateContent(
        contents,
        settings = GenerateContentSettings(
          model = NonOpenAIModelId.gemini_3_flash_preview,
          systemInstruction = Some(systemPrompt),
          generationConfig = Some(
            GenerationConfig(
              maxOutputTokens = Some(4000),
              temperature = Some(0.2),
              thinkingConfig = Some(
                ThinkingConfig(
                  thinkingLevel = Some(ThinkingLevel.MINIMAL)
              )
              )
            )
          )
        )
      )
      .map { response =>
        println(response.contentHeadText)
        println(response.usageMetadata)
      }
}
