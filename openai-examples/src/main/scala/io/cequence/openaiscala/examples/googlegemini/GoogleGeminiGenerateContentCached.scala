package io.cequence.openaiscala.examples.googlegemini

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.domain.{CachedContent, Content, Expiration}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.io.Source

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiGenerateContentCached extends ExampleBase[GeminiService] {

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

  private val knowledgeTextContent: Content =
    Content.textPart(
      knowledgeContent,
      User
    )

  override protected def run: Future[_] = {
    def listCachedContents =
      service.listCachedContents().map { cachedContentsResponse =>
        logger.info(
          s"Cached contents: ${cachedContentsResponse.cachedContents.flatMap(_.name).mkString(", ")}"
        )
      }

    for {
      _ <- listCachedContents

      saveCachedContent <- service.createCachedContent(
        CachedContent(
          contents = Seq(knowledgeTextContent),
          systemInstruction = Some(Content.textPart(systemPrompt, User)),
          model = model
        )
      )

      cachedContentName = saveCachedContent.name.get

      _ = logger.info(s"${cachedContentName} - expire time : " + saveCachedContent.expireTime)

      _ <- listCachedContents

      updatedCachedContent <- service.updateCachedContent(
        cachedContentName,
        Expiration.TTL("60s")
      )

      _ = logger.info(
        s"${cachedContentName} - new expire time : " + updatedCachedContent.expireTime
      )

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

      _ = logger.info("Response : " + response.contentHeadText)

      _ = {
        val usage = response.usageMetadata
        logger.info(
          s"""Usage
             |Prompt tokens     : ${usage.promptTokenCount}
             |(cached)          : ${usage.cachedContentTokenCount.getOrElse(0)}
             |Candidate tokens: : ${usage.candidatesTokenCount.getOrElse(0)}
             |Total tokens      : ${usage.totalTokenCount}""".stripMargin
        )
      }

      cachedContentNameNew <- service.getCachedContent(cachedContentName)

      _ = logger.info(
        s"${cachedContentNameNew.name.get} - expire time : " + cachedContentNameNew.expireTime
      )

      _ <- service.deleteCachedContent(cachedContentName)

      _ <- listCachedContents
    } yield ()
  }
}
