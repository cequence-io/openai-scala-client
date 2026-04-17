package io.cequence.openaiscala.examples.googlegemini

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  FunctionCallSpec,
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.response.GenerateContentResponse
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.io.Source

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiCreateChatToolCompletionCachedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = GeminiServiceFactory.asOpenAI()

  protected val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val systemPrompt = "You are a helpful assistant and expert in Norway."
  private val userPrompt1 = "Summarize the section 'Higher education in Norway'."
  private val userPrompt2 = "Summarize the section 'Music'."
  private lazy val knowledgeContent = {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/norway_wiki.md"))
    try source.mkString("")
    finally source.close()
  }

  private val model = NonOpenAIModelId.gemini_2_0_flash_lite

  private val systemMessage = SystemMessage(systemPrompt + "\n" + knowledgeContent)

  private val tools = Seq(
    FunctionTool(
      name = "write_summary",
      description = Some("Write a concise summary of the requested section"),
      parameters = JsonSchema.Object(
        properties = Seq(
          "section" -> JsonSchema.String(description = Some("Section name")),
          "summary" -> JsonSchema.String(description = Some("Summary of the section"))
        ),
        required = Seq("section", "summary")
      )
    )
  )

  override protected def run: Future[_] =
    for {
      // First call: enable caching. Before bug_009 fix this was silently ignored on the tool path.
      response1 <- service.createChatToolCompletion(
        messages = Seq(systemMessage, UserMessage(userPrompt1)),
        tools = tools,
        responseToolChoice = None,
        settings = CreateChatCompletionSettings(model = model).enableCacheSystemMessage(true)
      )
      _ = reportResponse("Call 1 (enableCache)", response1)
      cacheName = getCacheName(response1)

      // Second call: reuse the cache by name.
      response2 <- service.createChatToolCompletion(
        messages = Seq(systemMessage, UserMessage(userPrompt2)),
        tools = tools,
        responseToolChoice = None,
        settings = CreateChatCompletionSettings(model = model).setSystemCacheName(cacheName)
      )
      _ = reportResponse("Call 2 (setSystemCacheName)", response2)
    } yield ()

  private def reportResponse(
    label: String,
    response: ChatToolCompletionResponse
  ): Unit = {
    logger.info(s"=== $label ===")

    val toolCalls = response.choices.headOption
      .map(_.message.tool_calls.collect { case (id, x: FunctionCallSpec) => (id, x) })
      .getOrElse(Nil)

    logger.info(s"tool call ids   : ${toolCalls.map(_._1).mkString(", ")}")
    logger.info(s"tool call names : ${toolCalls.map(_._2.name).mkString(", ")}")
    logger.info(s"tool call args  : ${toolCalls.map(_._2.arguments).mkString(" | ")}")

    val usage = response.usage.get
    logger.info(
      s"""Usage
         |Prompt tokens   : ${usage.prompt_tokens}
         |(cached)        : ${usage.prompt_tokens_details.map(_.cached_tokens).getOrElse(0)}
         |Response tokens : ${usage.completion_tokens.getOrElse(0)}
         |Total tokens    : ${usage.total_tokens}""".stripMargin
    )

    val originalUsage = response.originalResponse
      .getOrElse(throw new IllegalStateException("Original response not found"))
      .asInstanceOf[GenerateContentResponse]
      .usageMetadata

    logger.info(
      s"""Original Usage
         |Prompt tokens   : ${originalUsage.promptTokenCount}
         |(cached)        : ${originalUsage.cachedContentTokenCount.getOrElse(0)}
         |Candidate tokens: ${originalUsage.candidatesTokenCount.getOrElse(0)}
         |Total tokens    : ${originalUsage.totalTokenCount}""".stripMargin
    )

    logger.info(s"Cache name: ${getCacheName(response)}")
  }

  private def getCacheName(response: ChatToolCompletionResponse): String =
    response.originalResponse
      .getOrElse(throw new IllegalStateException("Original response not found"))
      .asInstanceOf[GenerateContentResponse]
      .cachedContent
      .getOrElse(throw new IllegalStateException("Cached content not found"))
}
