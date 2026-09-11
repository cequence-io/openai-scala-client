package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock
import io.cequence.openaiscala.anthropic.domain.Message.{
  SystemMessage => AntSystem,
  UserMessage => AntUser
}
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  DeltaBlock,
  MessageStreamEvent
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMessageSettings,
  OutputConfig,
  OutputEffort,
  ThinkingSettings
}
import io.cequence.openaiscala.anthropic.domain.tools.Tool
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  ModelId,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig,
  ThinkingConfig
}
import io.cequence.openaiscala.gemini.domain.{
  Content,
  FunctionDeclaration,
  Part,
  Schema,
  SchemaType,
  ThinkingLevel,
  Tool => GeminiTool
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.responsesapi.tools.{
  CodeInterpreterContainer,
  CodeInterpreterTool,
  WebSearchTool,
  FunctionTool => ResponsesFunctionTool
}
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ReasoningConfig
}

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps.RichCreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps.RichResponsesCreateChatCompletionSettings

import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps.RichGeminiCreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory,
  OpenAIServiceFactory
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Live probe of what the streaming APIs surface per provider - every raw event / part / chunk
 * is printed in a compact one-line form so it is easy to see which "tags" exist for thinking,
 * tool calls and tool results:
 *
 *   - Anthropic native `createMessageStreamedEvents` (adaptive thinking + custom tool, and the
 *     web_search server tool)
 *   - Anthropic through the OpenAI chat-completion adapter
 *   - Gemini native `generateContentStreamed` (includeThoughts + function tool, and code
 *     execution)
 *   - Gemini through the OpenAI chat-completion adapter
 *   - OpenAI chat completions with function tools (passed via `extra_params`)
 *   - the typed `createChatToolCompletionStreamed` / `createChatCompletionStreamedTyped`
 *     stream (ChatChunk) for Anthropic, Gemini, OpenAI and an OpenAI-compatible provider
 *     (DeepSeek)
 *
 * Plain `main` + `Await` so sbt's TrapExit does not swallow the output. Requires
 * `ANTHROPIC_API_KEY`, `GOOGLE_API_KEY`, `OPENAI_SCALA_CLIENT_API_KEY` (sections whose key is
 * missing are skipped).
 *
 * Run: `sbt "examples/runMain io.cequence.openaiscala.examples.StreamingEventsProbe"`
 */
object StreamingEventsProbe {

  private implicit val system: ActorSystem = ActorSystem("streaming-events-probe")
  private implicit val materializer: Materializer = Materializer(system)
  private implicit val ec: ExecutionContext = system.dispatcher

  private val weatherQuestion =
    "What is the weather in Oslo right now? You must call the get_weather tool to find out."

  private def section(title: String)(body: => Future[_]): Unit = {
    println(s"\n========== $title ==========")
    try Await.result(body, 180.seconds)
    catch {
      case NonFatal(e) =>
        println(s"[FAILED] ${e.getClass.getSimpleName}: ${e.getMessage.take(400)}")
    }
  }

  private def short(
    s: String,
    n: Int = 70
  ): String =
    s.replaceAll("\\s+", " ").take(n) + (if (s.length > n) "…" else "")

  private def hasKey(name: String): Boolean = sys.env.get(name).exists(_.nonEmpty)

  def main(args: Array[String]): Unit = {
    if (hasKey("ANTHROPIC_API_KEY")) {
      val anthropic = AnthropicServiceFactory()
      val weatherTool = Tool.custom(
        name = "get_weather",
        inputSchema = JsonSchema.Object(
          properties = Seq("location" -> JsonSchema.String(description = Some("City name"))),
          required = Seq("location")
        ),
        description = Some("Get the current weather in a city")
      )

      section("Anthropic native events - adaptive thinking + custom tool (claude-sonnet-5)") {
        anthropic
          .createMessageStreamedEvents(
            Seq(AntSystem("You are a terse assistant."), AntUser(weatherQuestion)),
            AnthropicCreateMessageSettings(
              model = NonOpenAIModelId.claude_sonnet_5,
              max_tokens = 2048,
              thinking = Some(ThinkingSettings.adaptive),
              output_config = Some(OutputConfig(effort = Some(OutputEffort.low))),
              tools = Seq(weatherTool)
            )
          )
          .runWith(Sink.foreach(printAnthropicEvent))
      }

      section("Anthropic native events - web_search server tool (claude-sonnet-5)") {
        anthropic
          .createMessageStreamedEvents(
            Seq(
              AntUser(
                "Search the web and tell me in one sentence: what is the most recent news about missions to Jupiter?"
              )
            ),
            AnthropicCreateMessageSettings(
              model = NonOpenAIModelId.claude_sonnet_5,
              max_tokens = 2048,
              tools = Seq(Tool.webSearch())
            )
          )
          .runWith(Sink.foreach(printAnthropicEvent))
      }

      val anthropicAsOpenAI = AnthropicServiceFactory.asOpenAI()
      section("Anthropic via OpenAI adapter chunks - reasoning_effort=low (claude-sonnet-5)") {
        anthropicAsOpenAI
          .createChatCompletionStreamed(
            Seq(
              SystemMessage("You are a terse assistant."),
              UserMessage("Why is the sky blue? One sentence.")
            ),
            CreateChatCompletionSettings(
              model = NonOpenAIModelId.claude_sonnet_5,
              max_tokens = Some(600),
              reasoning_effort = Some(ReasoningEffort.low)
            )
          )
          .runWith(Sink.foreach(printOpenAIChunk))
      }
      anthropicAsOpenAI.close()
      anthropic.close()
    } else println("[anthropic] skipped - ANTHROPIC_API_KEY not set")

    if (hasKey("GOOGLE_API_KEY")) {
      val gemini = GeminiServiceFactory()
      val weatherDecl = GeminiTool.FunctionDeclarations(
        Seq(
          FunctionDeclaration(
            name = "get_weather",
            description = "Get the current weather in a city",
            parameters = Some(
              Schema(
                `type` = SchemaType.OBJECT,
                properties = Some(Map("location" -> Schema(`type` = SchemaType.STRING))),
                required = Some(Seq("location"))
              )
            )
          )
        )
      )
      val thinking = GenerationConfig(
        thinkingConfig = Some(
          ThinkingConfig(includeThoughts = Some(true), thinkingLevel = Some(ThinkingLevel.LOW))
        )
      )

      section("Gemini native parts - includeThoughts + function tool (gemini-3.8-flash)") {
        gemini
          .generateContentStreamed(
            Seq(Content.textPart(weatherQuestion, User)),
            GenerateContentSettings(
              model = NonOpenAIModelId.gemini_3_8_flash,
              tools = Some(Seq(weatherDecl)),
              generationConfig = Some(thinking)
            )
          )
          .runWith(Sink.foreach(printGeminiResponse))
      }

      section("Gemini native parts - code execution tool (gemini-3.8-flash)") {
        gemini
          .generateContentStreamed(
            Seq(
              Content.textPart(
                "Compute the 30th Fibonacci number by writing and running Python code, then state the result.",
                User
              )
            ),
            GenerateContentSettings(
              model = NonOpenAIModelId.gemini_3_8_flash,
              tools = Some(Seq(GeminiTool.CodeExecution)),
              generationConfig = Some(thinking)
            )
          )
          .runWith(Sink.foreach(printGeminiResponse))
      }

      val geminiAsOpenAI = GeminiServiceFactory.asOpenAI()
      section("Gemini via OpenAI adapter chunks - reasoning_effort=low (gemini-3.8-flash)") {
        geminiAsOpenAI
          .createChatCompletionStreamed(
            Seq(
              SystemMessage("You are a terse assistant."),
              UserMessage("Why is the sky blue? One sentence.")
            ),
            CreateChatCompletionSettings(
              model = NonOpenAIModelId.gemini_3_8_flash,
              max_tokens = Some(600),
              reasoning_effort = Some(ReasoningEffort.low)
            )
          )
          .runWith(Sink.foreach(printOpenAIChunk))
      }
      geminiAsOpenAI.close()
      gemini.close()
    } else println("[gemini] skipped - GOOGLE_API_KEY not set")

    if (hasKey("OPENAI_SCALA_CLIENT_API_KEY")) {
      val openAI = OpenAIServiceFactory.withStreaming()
      section(
        "OpenAI chat completions chunks - gpt-5.4 + function tool via extra_params + stream_options usage"
      ) {
        openAI
          .createChatCompletionStreamed(
            Seq(SystemMessage("You are a terse assistant."), UserMessage(weatherQuestion)),
            CreateChatCompletionSettings(
              model = ModelId.gpt_5_4,
              // GPT-5.4 rejects function tools with reasoning on the chat completions API
              reasoning_effort = Some(ReasoningEffort.none),
              extra_params = Map(
                "tools" -> Seq(
                  Map(
                    "type" -> "function",
                    "function" -> Map(
                      "name" -> "get_weather",
                      "description" -> "Get the current weather in a city",
                      "parameters" -> Map(
                        "type" -> "object",
                        "properties" -> Map("location" -> Map("type" -> "string")),
                        "required" -> Seq("location")
                      )
                    )
                  )
                ),
                "stream_options" -> Map("include_usage" -> true)
              )
            )
          )
          .runWith(Sink.foreach(printOpenAIChunk))
      }
      openAI.close()
    } else println("[openai] skipped - OPENAI_SCALA_CLIENT_API_KEY not set")

    // ---------------------------------------------------------------------------------------
    // The typed stream (createChatToolCompletionStreamed) - the same ChatChunk ADT for all
    // ---------------------------------------------------------------------------------------
    val weatherTool = FunctionTool(
      name = "get_weather",
      description = Some("Get the current weather in a city"),
      parameters = JsonSchema.Object(
        properties = Seq("location" -> JsonSchema.String(description = Some("City name"))),
        required = Seq("location")
      )
    )
    val typedMessages =
      Seq(SystemMessage("You are a terse assistant."), UserMessage(weatherQuestion))

    if (hasKey("ANTHROPIC_API_KEY")) {
      val anthropic = AnthropicServiceFactory.asOpenAI()
      section(
        "TYPED Anthropic - thinking + get_weather tool + web_search server tool (claude-sonnet-5)"
      ) {
        anthropic
          .createChatToolCompletionStreamed(
            Seq(
              SystemMessage("You are a terse assistant."),
              UserMessage(
                "Search the web for the most recent news about missions to Jupiter (one sentence), then call get_weather for Oslo."
              )
            ),
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(
              model = NonOpenAIModelId.claude_sonnet_5,
              max_tokens = Some(4000),
              reasoning_effort = Some(ReasoningEffort.medium)
            ).setAnthropicTools(Seq(Tool.webSearch()))
          )
          .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
      }
      anthropic.close()
    }

    if (hasKey("GOOGLE_API_KEY")) {
      val gemini = GeminiServiceFactory.asOpenAI()
      section("TYPED Gemini - thoughts + get_weather tool (gemini-3.8-flash)") {
        gemini
          .createChatToolCompletionStreamed(
            typedMessages,
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(
              model = NonOpenAIModelId.gemini_3_8_flash,
              reasoning_effort = Some(ReasoningEffort.low)
            )
          )
          .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
      }
      section("TYPED Gemini - code execution server tool (gemini-3.8-flash)") {
        gemini
          .createChatToolCompletionStreamed(
            Seq(
              UserMessage(
                "Compute the 30th Fibonacci number by writing and running Python code, then state the result."
              )
            ),
            Nil,
            None,
            CreateChatCompletionSettings(
              model = NonOpenAIModelId.gemini_3_8_flash,
              reasoning_effort = Some(ReasoningEffort.low)
            ).setGeminiTools(Seq(GeminiTool.CodeExecution))
          )
          .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
      }
      gemini.close()
    }

    if (hasKey("OPENAI_SCALA_CLIENT_API_KEY")) {
      val openAI = OpenAIServiceFactory.withStreaming()
      section("TYPED OpenAI - get_weather tool (gpt-5.4, reasoning none)") {
        openAI
          .createChatToolCompletionStreamed(
            typedMessages,
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(
              model = ModelId.gpt_5_4,
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
      }
      section(
        "TYPED Responses API - reasoning summary + web_search + code_interpreter + get_weather (gpt-5.4)"
      ) {
        openAI
          .createModelResponseStreamedTyped(
            Inputs.Text(
              "First search the web for the most recent news about missions to Jupiter (one sentence). " +
                "Then compute the 30th Fibonacci number with python. Then call get_weather for Oslo."
            ),
            CreateModelResponseSettings(
              model = ModelId.gpt_5_4,
              reasoning = Some(
                ReasoningConfig(effort = Some(ReasoningEffort.low), summary = Some("auto"))
              ),
              tools = Seq(
                WebSearchTool(),
                CodeInterpreterTool(container = CodeInterpreterContainer.Auto()),
                ResponsesFunctionTool(
                  name = "get_weather",
                  parameters = JsonSchema.Object(
                    properties = Seq("location" -> JsonSchema.String()),
                    required = Seq("location")
                  ),
                  strict = false,
                  description = Some("Get the current weather in a city")
                )
              )
            )
          )
          .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
      }
      section(
        "TYPED chat-shaped Responses API - messages + CreateChatCompletionSettings, reasoning + web_search + get_weather (gpt-5.4)"
      ) {
        openAI
          .createChatToolCompletionStreamedViaResponses(
            Seq(
              SystemMessage("You are a terse assistant."),
              UserMessage(
                "Search the web for the most recent news about missions to Jupiter (one sentence), then call get_weather for Oslo."
              )
            ),
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(
              model = ModelId.gpt_5_4,
              reasoning_effort = Some(ReasoningEffort.low)
            ).setResponsesTools(Seq(WebSearchTool()))
          )
          .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
      }
      section("TYPED GPT-6 Astra - get_weather tool routed through the Responses API") {
        openAI
          .createChatToolCompletionStreamed(
            typedMessages,
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(
              model = ModelId.gpt_6_astra,
              reasoning_effort = Some(ReasoningEffort.low)
            )
          )
          .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
      }
      if (hasKey("DEEPSEEK_API_KEY"))
        section(
          "TYPED DeepSeek - reasoning_content via the generic OpenAI-compatible path (deepseek-v4-flash)"
        ) {
          val deepseek =
            OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.deepseek)
          deepseek
            .createChatCompletionStreamedTyped(
              Seq(UserMessage("Why is the sky blue? One sentence.")),
              CreateChatCompletionSettings(
                model = NonOpenAIModelId.deepseek_v4_flash,
                extra_params = Map("thinking" -> Map("type" -> "enabled"))
              )
            )
            .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
            .andThen { case _ => deepseek.close() }
        }
      openAI.close()
    }

    // ---------------------------------------------------------------------------------------
    // OpenAI-compatible providers through the generic typed path (tools + reasoning fields)
    // ---------------------------------------------------------------------------------------
    val compatible: Seq[
      (
        String,
        String,
        io.cequence.openaiscala.domain.ProviderSettings,
        String,
        Map[String, Any]
      )
    ] = Seq(
      (
        "grok",
        "GROK_API_KEY",
        ChatProviderSettings.grok,
        NonOpenAIModelId.grok_4_6,
        Map.empty
      ),
      (
        "groq",
        "GROQ_API_KEY",
        ChatProviderSettings.groq,
        NonOpenAIModelId.groq_qwen3_8_27b,
        Map("reasoning_format" -> "parsed")
      ),
      (
        "cerebras",
        "CEREBRAS_API_KEY",
        ChatProviderSettings.cerebras,
        NonOpenAIModelId.cerebras_qwen_3_8_27b,
        Map.empty
      ),
      (
        "fireworks",
        "FIREWORKS_API_KEY",
        ChatProviderSettings.fireworks,
        "accounts/fireworks/models/" + NonOpenAIModelId.gpt_oss_120b,
        Map.empty
      )
    )
    compatible.foreach { case (name, keyEnv, provider, model, extraParams) =>
      if (hasKey(keyEnv)) {
        val service = OpenAIChatCompletionServiceFactory.withStreaming(provider)
        section(s"TYPED $name - get_weather tool + reasoning ($model)") {
          service
            .createChatToolCompletionStreamed(
              typedMessages,
              Seq(weatherTool),
              None,
              CreateChatCompletionSettings(
                model = model,
                reasoning_effort = Some(ReasoningEffort.low),
                extra_params = extraParams
              )
            )
            .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
        }
        section(s"TYPED $name - reasoning only, no tools ($model)") {
          service
            .createChatCompletionStreamedTyped(
              Seq(UserMessage("Why is the sky blue? One sentence.")),
              CreateChatCompletionSettings(
                model = model,
                reasoning_effort = Some(ReasoningEffort.low),
                extra_params = extraParams
              )
            )
            .runWith(Sink.foreach(chunk => println(ChatChunkPrinter.describe(chunk))))
        }
        service.close()
      }
    }

    Await.ready(system.terminate(), 30.seconds)
    ()
  }

  private def printAnthropicEvent(event: MessageStreamEvent): Unit = event match {
    case MessageStreamEvent.MessageStart(m) =>
      println(s"message_start        id=${m.id} model=${m.model} usage=${m.usage}")
    case MessageStreamEvent.ContentBlockStart(index, blockType, block, _) =>
      val detail = block match {
        case Some(ContentBlock.ToolUseBlock(id, name, _)) => s"tool_use id=$id name=$name"
        case Some(ContentBlock.ServerToolUseBlock(id, name, _)) =>
          s"server_tool_use id=$id name=$name"
        case Some(b) => b.getClass.getSimpleName
        case None    => "(block not parsed at start)"
      }
      println(s"content_block_start  [$index] type=$blockType  $detail")
    case MessageStreamEvent.ContentBlockDeltaEvent(ContentBlockDelta(_, index, delta)) =>
      val (kind, payload) = delta match {
        case DeltaBlock.DeltaText(t)       => ("text_delta", short(t))
        case DeltaBlock.DeltaThinking(t)   => ("thinking_delta", short(t))
        case DeltaBlock.DeltaSignature(s)  => ("signature_delta", s"(${s.length} chars)")
        case DeltaBlock.DeltaInputJson(j)  => ("input_json_delta", short(j))
        case DeltaBlock.DeltaCitations(c)  => ("citations_delta", short(c.toString))
        case DeltaBlock.DeltaUnknown(t, r) => (s"UNKNOWN($t)", short(r.toString))
      }
      println(f"content_block_delta  [$index] $kind%-18s $payload")
    case MessageStreamEvent.ContentBlockStop(index) =>
      println(s"content_block_stop   [$index]")
    case MessageStreamEvent.MessageDelta(stop, _, usage) =>
      println(s"message_delta        stop_reason=$stop usage=$usage")
    case MessageStreamEvent.MessageStop => println("message_stop")
    case MessageStreamEvent.Ping        => println("ping")
    case MessageStreamEvent.UnknownEvent(t, raw) =>
      println(s"UNKNOWN EVENT        type=$t ${short(raw.toString, 120)}")
  }

  private def printGeminiResponse(
    r: io.cequence.openaiscala.gemini.domain.response.GenerateContentResponse
  ): Unit = {
    r.candidates.foreach { c =>
      c.content.parts.foreach {
        case Part.Text(text, thought, sig) =>
          val tag = if (thought.contains(true)) "text(thought=true)" else "text"
          println(f"part $tag%-22s sig=${sig.isDefined}%-5s ${short(text)}")
        case Part.FunctionCall(id, name, args, sig) =>
          println(
            f"part functionCall${""}%-10s id=$id name=$name args=$args sig=${sig.isDefined}"
          )
        case Part.ExecutableCode(lang, code) =>
          println(f"part executableCode${""}%-8s lang=$lang ${short(code)}")
        case Part.CodeExecutionResult(outcome, output) =>
          println(
            f"part codeExecutionResult   outcome=$outcome ${short(output.getOrElse(""))}"
          )
        case Part.Unknown(data) => println(s"part UNKNOWN ${short(data.toString, 120)}")
        case other              => println(s"part ${other.getClass.getSimpleName}")
      }
      c.finishReason.foreach(fr => println(s"  finishReason=$fr"))
    }
    r.usageMetadata.thoughtsTokenCount.foreach(t => println(s"  usage thoughtsTokenCount=$t"))
  }

  private def printOpenAIChunk(
    chunk: io.cequence.openaiscala.domain.response.ChatCompletionChunkResponse
  ): Unit =
    chunk.choices.foreach { ch =>
      val d = ch.delta
      val tools = d.tool_calls.map(
        _.map(t =>
          s"tool_call[idx=${t.index} id=${t.id.getOrElse("-")} name=${t.function
              .flatMap(_.name)
              .getOrElse("-")} args=${short(t.function.flatMap(_.arguments).getOrElse(""), 40)}]"
        ).mkString(" ")
      )
      val parts = Seq(
        d.role.map(r => s"role=$r"),
        d.content.map(c => s"content=${short(c, 50)}"),
        tools,
        ch.finish_reason.map(f => s"finish_reason=$f"),
        chunk.usage.map(u => s"usage=$u")
      ).flatten
      if (parts.nonEmpty) println(s"chunk  ${parts.mkString("  ")}")
    }
}
