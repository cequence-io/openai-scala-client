package io.cequence.openaiscala.examples

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionTool,
  JsonSchema,
  ModelId,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIServiceFactory}
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

/**
 * The PLAIN (non-streamed) `createChatToolCompletion` with a provider-neutral `MCPServerTool`
 * (keyless DeepWiki) next to an ordinary function tool, on OpenAI (routed through the
 * Responses API), Anthropic and Gemini. Each provider runs the MCP tool itself and answers
 * from it; the function tool stays the caller's - a call to it, if the model makes one, comes
 * back in `tool_calls`. Gemini cannot combine `mcpServers` with any other tool type, so its
 * leg sends the MCP server alone (the adapter fails fast otherwise).
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY`, `ANTHROPIC_API_KEY` and `GOOGLE_API_KEY`.
 */
object CreateChatToolCompletionWithMCPServerTool extends ExampleBase[CloseableService] {

  private val openAI = OpenAIServiceFactory()
  private val anthropic = AnthropicServiceFactory.asOpenAI()
  private val gemini = GeminiServiceFactory.asOpenAI()

  override protected val service: CloseableService = new CloseableService {
    override def close(): Unit = {
      openAI.close()
      anthropic.close()
      gemini.close()
    }
  }

  private val deepwiki = MCPServerTool(name = "deepwiki", url = "https://mcp.deepwiki.com/mcp")

  private val tools = Seq(
    deepwiki,
    FunctionTool(
      name = "get_weather",
      description = Some("Get the current weather in a city"),
      parameters = JsonSchema.Object(
        properties = Seq("location" -> JsonSchema.String(description = Some("City name"))),
        required = Seq("location")
      )
    )
  )

  private val messages = Seq(
    SystemMessage("You are a terse assistant. Use the deepwiki tools to look things up."),
    UserMessage(
      "In one sentence: what is the 'given' keyword for in Scala 3? Check the scala/scala3 repository. " +
        "Do not call get_weather."
    )
  )

  private def run(
    label: String,
    service: OpenAIChatCompletionService,
    model: String,
    tools: Seq[ChatCompletionTool]
  ): Future[Unit] =
    service
      .createChatToolCompletion(
        messages = messages,
        tools = tools,
        settings = CreateChatCompletionSettings(model = model, max_tokens = Some(2000))
      )
      .map { response =>
        val choice = response.choices.head
        println("=" * 60)
        println(s"$label ($model)")
        println("=" * 60)
        println(s"finish     : ${choice.finish_reason.getOrElse("-")}")
        println(s"content    : ${choice.message.content.getOrElse("")}")
        println(s"tool_calls : ${choice.message.tool_calls.map(_._2)}")
        println(s"usage      : ${response.usage}")
      }

  override protected def run: Future[_] =
    for {
      _ <- run("OpenAI (Responses API)", openAI, ModelId.gpt_5_4, tools)
      _ <- run("Anthropic (MCP connector)", anthropic, NonOpenAIModelId.claude_sonnet_5, tools)
      // Gemini refuses mcpServers next to any other tool type - the MCP server goes alone
      _ <- run("Gemini (mcpServers)", gemini, NonOpenAIModelId.gemini_2_5_flash, Seq(deepwiki))
    } yield ()
}
