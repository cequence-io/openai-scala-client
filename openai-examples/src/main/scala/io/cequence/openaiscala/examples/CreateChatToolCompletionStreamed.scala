package io.cequence.openaiscala.examples

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.{JsonSchema, ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

/**
 * Typed streaming with a function tool: every [[ChatChunk]] is printed as it arrives, then the
 * stream is folded into an [[io.cequence.openaiscala.domain.response.AssembledChatCompletion]]
 * whose assembled tool calls could be executed and fed back via `toAssistantToolMessage`.
 *
 * Requires `openai-scala-client-stream` as a dependency.
 */
object CreateChatToolCompletionStreamed extends ExampleBase[OpenAIStreamedService] {

  override val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()

  private val messages = Seq(
    SystemMessage("You are a terse assistant."),
    UserMessage("What is the weather in Oslo right now? Use the get_weather tool to find out.")
  )

  private val tools = Seq(
    FunctionTool(
      name = "get_weather",
      description = Some("Get the current weather in a city"),
      parameters = JsonSchema.Object(
        properties = Seq("location" -> JsonSchema.String(description = Some("City name"))),
        required = Seq("location")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatToolCompletionStreamed(
        messages = messages,
        tools = tools,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_5_4,
          // GPT-5.4 accepts function tools on the chat completions API only without reasoning
          reasoning_effort = Some(ReasoningEffort.none)
        )
      )
      .alsoTo(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
      .assembled
      .map { assembled =>
        println()
        println(s"text        : ${assembled.text}")
        println(s"finish      : ${assembled.finishReason} (${assembled.providerFinishReason})")
        println(
          s"tool calls  : ${assembled.clientToolCalls.map(tc => s"${tc.toolName}(${tc.arguments})")}"
        )
        println(s"usage       : ${assembled.usage}")
        println(s"next turn   : ${assembled.toAssistantToolMessage}")
      }
}

/** One-line rendering of a [[ChatChunk]] shared by the typed streaming examples. */
object ChatChunkPrinter {

  def describe(chunk: ChatChunk): String =
    chunk match {
      case Start(id, model) => s"start      : $model ($id)"
      case Text(text)       => s"text       : $text"
      case Thinking(text)   => s"thinking   : $text"
      case ThinkingSignature(sig, callId) =>
        s"signature  : ${sig.length} chars${callId.fold("")(id => s" (call $id)")}"
      case RedactedThinking(_) => "thinking   : [redacted]"
      case ToolCallStart(index, callId, name, serverSide) =>
        s"tool start : #$index $name ($callId${if (serverSide) ", server-side" else ""})"
      case ToolCallDelta(index, fragment)    => s"tool args  : #$index $fragment"
      case ToolCall(index, _, name, args, _) => s"tool call  : #$index $name($args)"
      case ToolResult(_, name, _, text, isError) =>
        s"tool result: $name error=$isError ${text.getOrElse("").replaceAll("\\s+", " ").take(120)}"
      case CodeExecution(callId, language, code) =>
        s"code exec  : ${language.getOrElse("?")} (${callId
            .take(12)}) ${code.replaceAll("\\s+", " ").take(100)}"
      case CodeExecutionResult(_, output, isError, _) =>
        s"code result: error=$isError ${output.getOrElse("").replaceAll("\\s+", " ").take(100)}"
      case WebSearch(_, queries) => s"web search : ${queries.mkString(" | ")}"
      case WebSearchResult(_, results, _) =>
        s"web results: ${results.map(_.url).mkString(", ").take(160)}"
      case Image(mime, b64, url) =>
        s"image      : ${mime.getOrElse("")} ${url.getOrElse(b64.map(d => s"${d.length} b64 chars").getOrElse(""))}"
      case Refusal(text) => s"refusal    : $text"
      case Citation(_, url, title, _) =>
        s"citation   : ${title.getOrElse("")} ${url.getOrElse("")}"
      case Finish(reason, provider) => s"finish     : $reason (${provider.getOrElse("-")})"
      case Usage(usage)             => s"usage      : $usage"
      case Other(kind, _)           => s"other      : $kind"
    }
}
