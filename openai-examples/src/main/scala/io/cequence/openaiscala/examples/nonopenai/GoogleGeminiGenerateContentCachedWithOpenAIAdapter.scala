package io.cequence.openaiscala.examples.nonopenai

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.response.GenerateContentResponse
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.io.Source

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiGenerateContentCachedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = GeminiServiceFactory.asOpenAI()

  protected val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val systemPrompt = "You are a helpful assistant and expert in Norway."
  private val userPrompt1 = "Write the section 'Higher education in Norway' verbatim."
  private val userPrompt2 = "Write the section 'Music' verbatim."
  private val knowledgeFile = getClass.getResource("/norway_wiki.md").getFile

  private lazy val knowledgeContent = {
    val source = Source.fromFile(knowledgeFile)
    try source.mkString("")
    finally source.close()
  }

  private val model = NonOpenAIModelId.gemini_1_5_flash_002

  private val systemMessage = SystemMessage(systemPrompt + "\n" + knowledgeContent)

  override protected def run: Future[_] =
    for {
      response <- service.createChatCompletion(
        messages = Seq(
          systemMessage,
          UserMessage(userPrompt1)
        ),
        settings = CreateChatCompletionSettings(
          model = model
        ).enableCacheSystemMessage(true)
      )

      _ = reportResponse(response)

      cacheName = getCacheName(response)

      response2 <- service.createChatCompletion(
        messages = Seq(
          systemMessage,
          UserMessage(userPrompt1)
        ),
        settings = CreateChatCompletionSettings(
          model = model
        ).setSystemCacheName(cacheName)
      )

      _ = reportResponse(response2)

      response3 <- service.createChatCompletion(
        messages = Seq(
          systemMessage,
          UserMessage(userPrompt2)
        ),
        settings = CreateChatCompletionSettings(
          model = model
        ).setSystemCacheName(cacheName)
      )

      _ = reportResponse(response3)
    } yield ()

  private def reportResponse(response: ChatCompletionResponse): Unit = {
    val usage = response.usage.get

    logger.info(s"Response: ${response.contentHead}")

    logger.info(s"Cache name: ${getCacheName(response)}")

    logger.info(
      s"""Usage
           |Prompt tokens     : ${usage.prompt_tokens}
           |(cached)          : ${usage.prompt_tokens_details.get.cached_tokens}
           |Response tokens   : ${usage.completion_tokens.getOrElse(0)}
           |Total tokens      : ${usage.total_tokens}""".stripMargin
    )

    val originalUsage = response.originalResponse
      .getOrElse(
        throw new IllegalStateException("Original response not found")
      )
      .asInstanceOf[GenerateContentResponse]
      .usageMetadata

    logger.info(
      s"""Original Usage
         |Prompt tokens     : ${originalUsage.promptTokenCount}
         |(cached)          : ${originalUsage.cachedContentTokenCount.getOrElse(0)}
         |Candidate tokens: : ${originalUsage.candidatesTokenCount.getOrElse(0)}
         |Total tokens      : ${originalUsage.totalTokenCount}""".stripMargin
    )
  }

  private def getCacheName(response: ChatCompletionResponse) =
    response.originalResponse
      .getOrElse(
        throw new IllegalStateException("Original response not found")
      )
      .asInstanceOf[GenerateContentResponse]
      .cachedContent
      .getOrElse(
        throw new IllegalStateException("Cached content not found")
      )
}
