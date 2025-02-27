package io.cequence.openaiscala.examples.googlegemini

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.domain.{CachedContent, Content, Part}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}
import org.slf4j.LoggerFactory

import java.util.Base64
import scala.concurrent.Future
import scala.io.Source

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiGenerateContentCachedWithInlineData extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

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

  private val knowledgeInlineData: Content =
    Content(
      User,
      Part.InlineData(
        mimeType = "text/plain",
        data = Base64.getEncoder.encodeToString(knowledgeContent.getBytes("UTF-8"))
      )
    )

  override protected def run: Future[_] =
    for {
      // create cached content
      saveCachedContent <- service.createCachedContent(
        CachedContent(
          contents = Seq(knowledgeInlineData),
          systemInstruction = Some(Content.textPart(systemPrompt, User)),
          model = model
        )
      )

      cachedContentName = saveCachedContent.name.get

      _ = logger.info(s"${cachedContentName} - expire time : " + saveCachedContent.expireTime)

      // chat completion with cached content
      response <- service.generateContent(
        Seq(Content.textPart(userPrompt, User)),
        settings = GenerateContentSettings(
          model = model,
          generationConfig = Some(
            GenerationConfig(
              maxOutputTokens = Some(2000),
              temperature = Some(0.2)
            )
          ),
          cachedContent = Some(cachedContentName)
        )
      )

      // response
      _ = logger.info("Response: " + response.contentHeadText)

      // clean up
      _ <- service.deleteCachedContent(cachedContentName)
    } yield ()
}
