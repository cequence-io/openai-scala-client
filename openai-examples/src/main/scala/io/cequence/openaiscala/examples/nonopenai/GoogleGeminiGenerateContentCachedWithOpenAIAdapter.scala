package io.cequence.openaiscala.examples.nonopenai

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.{CachedContent, Content}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.io.Source

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiGenerateContentCachedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = GeminiServiceFactory.asOpenAI()

  private val rawGeminiService: GeminiService = GeminiServiceFactory()

  protected val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val systemPrompt = "You are a helpful assistant and expert in Norway."
  private val userPrompt = "Write the section 'Higher education in Norway' verbatim."
  private val knowledgeFile = getClass.getResource("/norway_wiki.md").getFile

  private lazy val knowledgeContent = {
    val source = Source.fromFile(knowledgeFile)
    try source.mkString("")
    finally source.close()
  }

  private val model = NonOpenAIModelId.gemini_1_5_flash_002

  private val knowledgeTextContent: Content =
    Content.textPart(
      knowledgeContent,
      User
    )

  // TODO
  override protected def run: Future[_] =
    for {
      saveCachedContent <- rawGeminiService.createCachedContent(
        CachedContent(
          contents = Seq(knowledgeTextContent),
          systemInstruction = Some(Content.textPart(systemPrompt, User)),
          model = model
        )
      )
//
//      response <- service.createChatCompletion(
//
//      )

      _ <- rawGeminiService.deleteCachedContent(saveCachedContent.name.get)
    } yield ()
}
