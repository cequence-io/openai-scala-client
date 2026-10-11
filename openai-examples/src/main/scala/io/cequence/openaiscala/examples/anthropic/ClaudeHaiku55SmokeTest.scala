package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.domain.tools.{MCPServerURLDefinition, Tool}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.{
  AssembledChatCompletion,
  ChatCompletionChunkResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.service.JsonSchemaReflectionHelper
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import play.api.libs.json.{Format, Json}

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of Claude Haiku 5.5 (`claude-haiku-5-5`, released 2026-10-07) through the
 * OpenAI adapter - what the client adapts for it, and the features it has to get right on it.
 * The sections come in groups, all run by default; pass group names to run some of them:
 *
 *   - `basics`: adaptive thinking + `output_config.effort` (low..max) with temperature / top_p
 *     dropped (the API takes only their defaults); `reasoning_effort = none` sent as thinking
 *     `disabled` (it thinks by default and rejects `between_tools`), also on the typed stream;
 *     a forced tool choice kept as it is (unlike Opus / Sonnet 5.5, it accepts `any` /
 *     `tool`); json_schema structured output
 *   - `json`: the JSON helper choosing json_schema by itself, with reasoning on; a schema
 *     derived from case classes (nested objects, a list, an optional field, a bounded integer
 *     whose `minimum` / `maximum` Anthropic refuses - the adapter drops them); structured
 *     output on the typed and on the OpenAI-shaped stream
 *   - `tools`: whole tool loops - the tool's result sent back and answered - with Haiku's
 *     default thinking, with `reasoning_effort = high`, on the typed stream, after a first
 *     turn that thought (its thinking blocks are not sent back - the OpenAI-shaped messages
 *     have no place for them), and for two cities at once (the parallel results go back as one
 *     user turn); a forced tool choice with reasoning on
 *   - `mcp`: DeepWiki's public MCP server through the provider-neutral `MCPServerTool` (sync:
 *     answered by the server, no client tool calls surfaced; typed stream: server-side
 *     `ToolCall` / `ToolResult`), with an allowed-tools filter, with reasoning, through
 *     `setAnthropicMcpServers`, next to a client function tool, and `requireApproval = true`
 *     refused before any I/O
 *   - `reasoning`: every effort level, `minimal` -> `low`, summarized thinking and its
 *     signatures on the typed stream, a thinking budget switched to adaptive thinking, the
 *     thinking as `reasoning_content` on the OpenAI-shaped stream
 *   - `other`: the OpenAI-shaped stream, an image, prompt caching (the cached tokens in the
 *     usage), Anthropic's web search tool
 *   - `bedrock`: json_schema with a Bedrock API key and with SigV4 keys, a tool loop, the
 *     typed stream with reasoning, an MCP server (Bedrock has no MCP connector), the regional
 *     profiles (`eu.` from an EU region, `us.` from a US one), `inferenceProfilePrefix` with
 *     the bare id, and the bare id without a profile (a 400)
 *
 * `target=bedrock` (a Bedrock API key) or `target=bedrock-sigv4` (SigV4 keys) runs the groups
 * against `global.anthropic.claude-haiku-5-5` on Bedrock instead of the Claude API (region
 * `AWS_BEDROCK_REGION`, else eu-central-1); there the `mcp` group checks only that an MCP
 * server is refused, and the web search section reports whether Bedrock serves the tool.
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed.
 * Requires `ANTHROPIC_API_KEY`; the Bedrock sections a Bedrock API key in
 * `AWS_BEARER_TOKEN_BEDROCK` and / or `AWS_BEDROCK_ACCESS_KEY` / `AWS_BEDROCK_SECRET_KEY` /
 * `AWS_BEDROCK_REGION` (skipped otherwise). A full run costs a few cents.
 */
object ClaudeHaiku55SmokeTest extends JsonSchemaReflectionHelper {

  private case class Capital(
    country: String,
    capital: String
  )

  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  private val capitalSchema = JsonSchemaDef(
    name = "capital",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "country" -> JsonSchema.String(),
          "capital" -> JsonSchema.String()
        ),
        required = Seq("country", "capital")
      )
    )
  )

  // an order read out of a message: nested objects, a list, an optional field and a bounded
  // integer (the range Anthropic refuses - the adapter drops it with a warning)
  case class LineItem(
    @JsonSchemaDescription("The product name") product: String,
    @JsonSchemaDescription("Units ordered") @JsonSchemaRange(1, 100) quantity: Int,
    @JsonSchemaDescription("Price per unit in EUR") unitPrice: Double
  )

  case class Order(
    @JsonSchemaDescription("The customer's full name") customer: String,
    items: Seq[LineItem],
    @JsonSchemaDescription("The gift note, if any") giftNote: Option[String]
  )

  implicit val lineItemFormat: Format[LineItem] = Json.format[LineItem]
  implicit val orderFormat: Format[Order] = Json.format[Order]

  private val orderSchema = JsonSchemaDef(
    name = "order",
    strict = true,
    jsonSchemaFor[Order]()
  )

  private val orderMessage = Seq(
    UserMessage(
      "Hi, this is Anna Novak. I'd like 2 espresso cups at 7.50 EUR each and 1 milk jug at " +
        "12 EUR. Please gift-wrap it with a note saying 'Happy birthday, Tom!'"
    )
  )

  private val weatherTool = FunctionTool(
    name = "get_weather",
    description = Some("Get the current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String(description = Some("City name"))),
      required = Seq("city")
    )
  )

  private val weatherQuestion = Seq(UserMessage("What's the weather in Paris right now?"))

  private val batAndBall =
    "A bat and a ball cost 1.10 in total; the bat costs 1.00 more than the ball. " +
      "Price of the ball?"

  // a decision to think through before the calls
  private val tripQuestion = Seq(
    UserMessage(
      "I'm choosing between Paris and Tokyo for a trip this weekend. Pick the warmer one, " +
        "unless it's raining there - then pick the other. Check the weather in both cities " +
        "and tell me which to choose."
    )
  )

  private val twoCitiesQuestion = Seq(
    UserMessage("What's the weather in Paris and in Tokyo right now? Check both cities.")
  )

  // what the "weather service" answers
  private def weatherFor(city: String): String =
    if (city.toLowerCase.contains("paris"))
      """{"city":"Paris","temperature_c":18,"condition":"light rain"}"""
    else if (city.toLowerCase.contains("tokyo"))
      """{"city":"Tokyo","temperature_c":24,"condition":"sunny"}"""
    else s"""{"city":"$city","temperature_c":20,"condition":"clear"}"""

  private def answerCalls(message: AssistantToolMessage): Seq[ToolMessage] =
    message.tool_calls.collect { case (id, fc: FunctionCallSpec) =>
      val city = Try((Json.parse(fc.arguments) \ "city").as[String]).getOrElse("")
      ToolMessage(Some(weatherFor(city)), id, fc.name)
    }

  private val deepwikiUrl = "https://mcp.deepwiki.com/mcp"

  private val deepwiki = ChatCompletionTool.MCPServerTool(name = "deepwiki", url = deepwikiUrl)

  private val mcpQuestion = Seq(
    SystemMessage("You are a terse assistant. Use the deepwiki tools to look things up."),
    UserMessage(
      "What is the main purpose of the scala/scala3 GitHub repository? Look it up with the " +
        "deepwiki tools and answer in one sentence."
    )
  )

  private val mcpStructureQuestion = Seq(
    SystemMessage("You are a terse assistant. Use the deepwiki tools to look things up."),
    UserMessage(
      "Which top-level documentation topics does DeepWiki list for the scala/scala3 " +
        "repository? Name three."
    )
  )

  private def functionCalls(response: ChatToolCompletionResponse): String =
    response.choices.head.message.tool_calls.collect { case (_, fc: FunctionCallSpec) =>
      s"${fc.name}(${fc.arguments})"
    }.mkString(", ") match {
      case ""    => throw new IllegalStateException("no tool call")
      case calls => calls
    }

  private def png(colour: Color): Array[Byte] = {
    val image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(colour)
    graphics.fillRect(0, 0, 64, 64)
    graphics.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    out.toByteArray
  }

  // a system prompt long enough to be cached (~10k tokens)
  private val longPolicy =
    (1 to 300)
      .map(i =>
        s"Rule $i: when a customer asks about order $i, check the warehouse log for shipment " +
          s"$i and reply with its status, the carrier and the expected delivery date; never " +
          "promise a date you cannot confirm."
      )
      .mkString("\n")

  private def describe(e: Throwable): String =
    s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("(no message)").take(400)}"

  private def check(
    condition: Boolean,
    what: => String
  ): Unit =
    if (!condition) throw new IllegalStateException(what)

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val groups = Seq("basics", "json", "tools", "mcp", "reasoning", "other", "bedrock")
    val target =
      args.find(_.startsWith("target=")).map(_.drop("target=".length)).getOrElse("api")
    val groupArgs = args.filterNot(_.startsWith("target="))
    val selected = if (groupArgs.isEmpty) groups.toSet else groupArgs.toSet

    val bedrockRegion =
      sys.env.get("AWS_BEDROCK_REGION").filter(_.nonEmpty).getOrElse("eu-central-1")
    val bedrockModel = "global." + NonOpenAIModelId.bedrock_claude_haiku_5_5
    val onBedrock = target != "api"

    // the service and the model every group runs against
    val anthropic: OpenAIChatCompletionStreamedService = target match {
      case "api" => AnthropicServiceFactory.asOpenAI()
      case "bedrock" =>
        AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(region = bedrockRegion)
      case "bedrock-sigv4" => AnthropicServiceFactory.bedrockAsOpenAI(region = bedrockRegion)
      case other =>
        throw new IllegalArgumentException(
          s"target=$other - use api, bedrock or bedrock-sigv4"
        )
    }
    val model = if (onBedrock) bedrockModel else NonOpenAIModelId.claude_haiku_5_5
    println(s"target $target, model $model")
    val failures = new AtomicInteger(0)

    def settings(
      maxTokens: Int = 4000,
      effort: Option[ReasoningEffort] = None,
      modelId: String = model
    ) =
      CreateChatCompletionSettings(
        model = modelId,
        max_tokens = Some(maxTokens),
        reasoning_effort = effort
      )

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      Try(f).fold(Future.failed, identity).transform {
        case Success(msg) =>
          println(s"[PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
          Success(())
        case Failure(e) =>
          failures.incrementAndGet()
          println(s"[FAIL] $name: ${describe(e)}")
          Success(())
      }
    }

    def group(
      name: String
    )(
      sections: => Future[Unit]
    ): Future[Unit] =
      if (!selected.contains(name)) Future.unit
      else {
        println(s"--- $name")
        sections
      }

    // a tool loop on the sync API: call, answer the tool calls, until the model answers
    def toolLoop(
      service: OpenAIChatCompletionStreamedService,
      question: Seq[BaseMessage],
      s: CreateChatCompletionSettings,
      expected: Seq[String]
    ): Future[String] = {
      def round(
        history: Seq[BaseMessage],
        calls: Seq[Int]
      ): Future[String] =
        service.createChatToolCompletion(history, Seq(weatherTool), settings = s).flatMap {
          response =>
            val assistant = response.choices.head.message
            if (assistant.tool_calls.isEmpty) {
              val text = assistant.content.getOrElse("")
              check(calls.nonEmpty, s"answered without calling the tool: '$text'")
              check(
                expected.forall(text.contains),
                s"the tool results ($expected) are not in the answer: '$text'"
              )
              Future.successful(s"tool calls per round $calls, then '${text.take(120)}'")
            } else if (calls.size >= 4)
              Future.failed(new IllegalStateException(s"still calling tools after $calls"))
            else
              round(
                history ++ (assistant +: answerCalls(assistant)),
                calls :+ assistant.tool_calls.size
              )
        }

      round(question, Nil)
    }

    // the same on the typed stream, continuing with the assembled assistant turn (which
    // carries no thinking blocks - the OpenAI-shaped messages have no place for them)
    def streamedToolLoop(
      service: OpenAIChatCompletionStreamedService,
      question: Seq[BaseMessage],
      s: CreateChatCompletionSettings,
      expected: Seq[String],
      firstRoundMustThink: Boolean = false
    ): Future[String] = {
      def round(
        history: Seq[BaseMessage],
        calls: Seq[Int],
        thinking: Seq[Int]
      ): Future[String] =
        service
          .createChatToolCompletionStreamed(history, Seq(weatherTool), None, s)
          .assembled
          .flatMap { assembled =>
            val assistant = assembled.toAssistantToolMessage
            val thought = thinking :+ assembled.thinking.length
            if (assistant.tool_calls.isEmpty) {
              check(calls.nonEmpty, s"answered without calling the tool: '${assembled.text}'")
              check(
                expected.forall(assembled.text.contains),
                s"the tool results ($expected) are not in the answer: '${assembled.text}'"
              )
              check(
                !firstRoundMustThink || thought.headOption.exists(_ > 0),
                s"the first round did not think ($thought) - nothing was dropped"
              )
              Future.successful(
                s"tool calls per round $calls, thinking chars per round $thought, finish " +
                  s"${assembled.finishReason}, then '${assembled.text.take(100)}'"
              )
            } else if (calls.size >= 4)
              Future.failed(new IllegalStateException(s"still calling tools after $calls"))
            else
              round(
                history ++ (assistant +: answerCalls(assistant)),
                calls :+ assistant.tool_calls.size,
                thought
              )
          }

      round(question, Nil, Nil)
    }

    def streamedText(chunks: Seq[ChatCompletionChunkResponse])
      : (String, String, Option[String]) =
      (
        chunks.flatMap(_.choices.headOption.flatMap(_.delta.content)).mkString,
        chunks.flatMap(_.choices.headOption.flatMap(_.delta.reasoning_content)).mkString,
        chunks.flatMap(_.choices.headOption.flatMap(_.finish_reason)).lastOption
      )

    def serverCalls(assembled: AssembledChatCompletion): String =
      s"server calls ${assembled.toolCalls.filter(_.serverSide).map(_.toolName)}, results " +
        s"${assembled.toolResults.size} (errors ${assembled.toolResults.count(_.isError)})"

    def withBedrock(
      envKey: String
    )(
      f: => Future[String]
    ): Future[String] =
      if (sys.env.get(envKey).forall(_.trim.isEmpty))
        Future.successful(s"skipped - $envKey not set")
      else f

    val all = for {
      _ <- group("basics") {
        for {
          _ <- section(
            s"$model: temperature/top_p dropped, reasoning_effort=xhigh -> adaptive"
          ) {
            anthropic
              .createChatCompletion(
                Seq(UserMessage("What is 17*23? Answer with the number only.")),
                settings(effort = Some(ReasoningEffort.xhigh))
                  .copy(temperature = Some(0.2), top_p = Some(0.5))
              )
              .map { r =>
                check(r.contentHead.contains("391"), s"wrong answer '${r.contentHead}'")
                s"content='${r.contentHead}'"
              }
          }

          _ <- section(s"$model: reasoning_effort=none -> thinking disabled") {
            anthropic
              .createChatCompletion(
                Seq(UserMessage("Say hi")),
                settings(maxTokens = 200, effort = Some(ReasoningEffort.none))
              )
              .map(r => s"content='${r.contentHead}'")
          }

          _ <- section(s"$model: typed stream, reasoning_effort=none + tool (no thinking)") {
            anthropic
              .createChatToolCompletionStreamed(
                weatherQuestion,
                Seq(weatherTool),
                None,
                settings(maxTokens = 2000, effort = Some(ReasoningEffort.none))
              )
              .assembled
              .map { a =>
                check(a.toolCalls.nonEmpty, s"no tool call: $a")
                check(a.thinking.isEmpty, s"thought: ${a.thinking}")
                s"calls=${a.toolCalls.map(_.toolName)} thinking=${a.thinking.length} chars finish=${a.finishReason}"
              }
          }

          _ <- section(s"$model: typed stream, reasoning_effort=max (summarized thinking)") {
            anthropic
              .createChatCompletionStreamedTyped(
                Seq(UserMessage("Is 391 prime? One sentence.")),
                settings(maxTokens = 8000, effort = Some(ReasoningEffort.max))
              )
              .assembled
              .map { a =>
                check(a.thinking.nonEmpty, "no thinking streamed")
                s"thinking=${a.thinking.length} chars text='${a.text.take(80)}'"
              }
          }

          _ <- section(s"$model: chat tools (auto)") {
            anthropic
              .createChatToolCompletion(
                weatherQuestion,
                Seq(weatherTool),
                settings = settings(maxTokens = 2000)
              )
              .map(functionCalls)
          }

          _ <- section(s"$model: forced tool choice (kept)") {
            anthropic
              .createChatToolCompletion(
                weatherQuestion,
                Seq(weatherTool),
                responseToolChoice = Some(weatherTool.name),
                settings = settings(maxTokens = 2000)
              )
              .map(functionCalls)
          }

          _ <- section(s"$model: createChatCompletionWithJSON[Capital] (json_schema)") {
            anthropic
              .createChatCompletionWithJSON[Capital](
                Seq(UserMessage("Capital of Norway?")),
                settings(maxTokens = 2000).copy(
                  response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
                  jsonSchema = Some(capitalSchema)
                )
              )
              .map { c =>
                check(c.capital.contains("Oslo"), s"wrong capital $c")
                s"parsed=$c"
              }
          }
        } yield ()
      }

      _ <- group("json") {
        for {
          _ <- section(
            s"$model: the JSON helper picks json_schema itself, reasoning_effort=high"
          ) {
            anthropic
              .createChatCompletionWithJSON[Capital](
                Seq(UserMessage("Capital of the country whose capital is Canberra?")),
                settings(effort = Some(ReasoningEffort.high))
                  .copy(jsonSchema = Some(capitalSchema))
              )
              .map { c =>
                check(c.capital.contains("Canberra"), s"wrong capital $c")
                s"parsed=$c"
              }
          }

          _ <- section(
            s"$model: a schema derived from case classes (nested, list, optional, bounds dropped)"
          ) {
            anthropic
              .createChatCompletionWithJSON[Order](
                orderMessage,
                settings().copy(jsonSchema = Some(orderSchema))
              )
              .map { order =>
                check(order.customer.contains("Anna"), s"customer ${order.customer}")
                check(order.items.size == 2, s"items ${order.items}")
                check(
                  order.items.map(_.quantity).sorted == Seq(1, 2),
                  s"quantities ${order.items}"
                )
                check(
                  order.giftNote.exists(_.contains("birthday")),
                  s"gift note ${order.giftNote}"
                )
                s"parsed=$order"
              }
          }

          _ <- section(s"$model: json_schema on the typed stream") {
            anthropic
              .createChatCompletionStreamedTyped(
                Seq(UserMessage("Capital of Japan?")),
                settings(maxTokens = 2000).copy(
                  response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
                  jsonSchema = Some(capitalSchema)
                )
              )
              .assembled
              .map { a =>
                val capital = Json.parse(a.text).as[Capital]
                check(capital.capital.contains("Tokyo"), s"wrong capital $capital")
                s"parsed=$capital from ${a.text.length} chars"
              }
          }

          _ <- section(s"$model: json_schema on the OpenAI-shaped stream") {
            anthropic
              .createChatCompletionStreamed(
                Seq(UserMessage("Capital of Kenya?")),
                settings(maxTokens = 2000).copy(
                  response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
                  jsonSchema = Some(capitalSchema)
                )
              )
              .runWith(Sink.seq)
              .map { chunks =>
                val (text, _, finish) = streamedText(chunks)
                val capital = Json.parse(text).as[Capital]
                check(capital.capital.contains("Nairobi"), s"wrong capital $capital")
                s"parsed=$capital from ${chunks.size} chunks, finish $finish"
              }
          }
        } yield ()
      }

      _ <- group("tools") {
        for {
          _ <- section(s"$model: a tool loop, Haiku's default thinking") {
            toolLoop(anthropic, weatherQuestion, settings(), Seq("18"))
          }

          _ <- section(s"$model: a tool loop, reasoning_effort=high") {
            toolLoop(
              anthropic,
              weatherQuestion,
              settings(effort = Some(ReasoningEffort.high)),
              Seq("18")
            )
          }

          _ <- section(s"$model: a tool loop on the typed stream, reasoning_effort=medium") {
            streamedToolLoop(
              anthropic,
              weatherQuestion,
              settings(effort = Some(ReasoningEffort.medium)),
              Seq("18")
            )
          }

          _ <- section(
            s"$model: a tool loop whose first turn thought (its thinking not sent back), effort max"
          ) {
            streamedToolLoop(
              anthropic,
              tripQuestion,
              settings(maxTokens = 16000, effort = Some(ReasoningEffort.max)),
              Seq("Tokyo"),
              firstRoundMustThink = true
            )
          }

          _ <- section(s"$model: two cities - parallel calls, their results in one turn") {
            toolLoop(anthropic, twoCitiesQuestion, settings(), Seq("18", "24"))
          }

          _ <- section(s"$model: forced tool choice + reasoning_effort=high") {
            anthropic
              .createChatToolCompletion(
                weatherQuestion,
                Seq(weatherTool),
                responseToolChoice = Some(weatherTool.name),
                settings = settings(effort = Some(ReasoningEffort.high))
              )
              .map(functionCalls)
          }
        } yield ()
      }

      _ <- group("mcp") {
        if (onBedrock)
          section(s"$model: an MCP server - Bedrock has no MCP connector (a classified 400)") {
            anthropic
              .createChatToolCompletion(mcpQuestion, Seq(deepwiki), settings = settings())
              .map(r =>
                s"answered?! '${r.choices.head.message.content.getOrElse("").take(80)}'"
              )
              .recover { case e: OpenAIScalaClientException =>
                s"refused: ${e.getClass.getSimpleName}: ${e.getMessage.take(160)}"
              }
              .map { msg =>
                check(!msg.startsWith("answered"), msg)
                msg
              }
          }
        else
          for {
            _ <- section(
              s"$model: MCPServerTool, sync - answered by the server, no client calls"
            ) {
              anthropic
                .createChatToolCompletion(
                  mcpQuestion,
                  Seq(deepwiki),
                  settings = settings(effort = Some(ReasoningEffort.low))
                )
                .map { response =>
                  val message = response.choices.head.message
                  check(
                    message.tool_calls.isEmpty,
                    s"server-side MCP calls surfaced as client tool calls: ${message.tool_calls}"
                  )
                  val text = message.content.getOrElse("")
                  check(text.nonEmpty, "no answer")
                  s"finish ${response.choices.head.finish_reason}, '${text.take(120)}'"
                }
            }

            _ <- section(
              s"$model: MCPServerTool on the typed stream - server-side calls and results"
            ) {
              anthropic
                .createChatToolCompletionStreamed(
                  mcpQuestion,
                  Seq(deepwiki),
                  None,
                  settings(effort = Some(ReasoningEffort.low))
                )
                .assembled
                .map { a =>
                  check(
                    a.toolCalls.exists(_.serverSide),
                    s"no server-side call: ${a.toolCalls}"
                  )
                  check(a.toolResults.nonEmpty, "no tool result")
                  check(a.clientToolCalls.isEmpty, s"client calls ${a.clientToolCalls}")
                  check(a.text.nonEmpty, "no answer")
                  s"${serverCalls(a)}, finish ${a.finishReason}, '${a.text.take(100)}'"
                }
            }

            _ <- section(s"$model: MCPServerTool with allowedTools = read_wiki_structure") {
              anthropic
                .createChatToolCompletionStreamed(
                  mcpStructureQuestion,
                  Seq(deepwiki.copy(allowedTools = Seq("read_wiki_structure"))),
                  None,
                  settings(effort = Some(ReasoningEffort.low))
                )
                .assembled
                .map { a =>
                  val names = a.toolCalls.filter(_.serverSide).map(_.toolName).toSet
                  check(names.nonEmpty, "no server-side call")
                  check(names.forall(_.contains("read_wiki_structure")), s"called $names")
                  s"${serverCalls(a)}, '${a.text.take(100)}'"
                }
            }

            _ <- section(
              s"$model: MCPServerTool + reasoning_effort=high (thinking and server calls)"
            ) {
              anthropic
                .createChatToolCompletionStreamed(
                  mcpQuestion,
                  Seq(deepwiki),
                  None,
                  settings(maxTokens = 8000, effort = Some(ReasoningEffort.high))
                )
                .assembled
                .map { a =>
                  check(a.toolCalls.exists(_.serverSide), "no server-side call")
                  check(a.text.nonEmpty, "no answer")
                  s"thinking ${a.thinking.length} chars, ${serverCalls(a)}"
                }
            }

            _ <- section(s"$model: setAnthropicMcpServers on createChatCompletion") {
              anthropic
                .createChatCompletion(
                  mcpQuestion,
                  settings(effort = Some(ReasoningEffort.low)).setAnthropicMcpServers(
                    Seq(MCPServerURLDefinition("deepwiki", deepwikiUrl))
                  )
                )
                .map { r =>
                  check(r.contentHead.nonEmpty, "no answer")
                  s"'${r.contentHead.take(120)}'"
                }
            }

            _ <- section(s"$model: an MCP server next to a client function tool") {
              anthropic
                .createChatToolCompletionStreamed(
                  weatherQuestion,
                  Seq(deepwiki, weatherTool),
                  None,
                  settings(effort = Some(ReasoningEffort.low))
                )
                .assembled
                .map { a =>
                  check(
                    a.clientToolCalls.exists(_.toolName == weatherTool.name),
                    s"no get_weather call: ${a.toolCalls}"
                  )
                  s"client calls ${a.clientToolCalls.map(_.toolName)}, server calls " +
                    s"${a.toolCalls.filter(_.serverSide).map(_.toolName)}, finish ${a.finishReason}"
                }
            }

            _ <- section(
              s"$model: MCPServerTool(requireApproval = true) refused before any I/O"
            ) {
              Future
                .fromTry(
                  Try(
                    anthropic.createChatToolCompletion(
                      mcpQuestion,
                      Seq(deepwiki.copy(requireApproval = true)),
                      settings = settings()
                    )
                  )
                )
                .flatten
                .map(_ => "answered?!")
                .recover { case e: OpenAIScalaClientException =>
                  s"refused: ${e.getMessage.take(110)}"
                }
                .map { msg =>
                  check(!msg.startsWith("answered"), msg)
                  msg
                }
            }
          } yield ()
      }

      _ <- group("reasoning") {
        for {
          _ <- Seq(ReasoningEffort.low, ReasoningEffort.medium, ReasoningEffort.high)
            .foldLeft(Future.unit) {
              (
                acc,
                effort
              ) =>
                acc.flatMap { _ =>
                  section(s"$model: reasoning_effort=$effort") {
                    anthropic
                      .createChatCompletion(
                        Seq(
                          UserMessage(
                            "How many prime numbers are there below 30? Answer with the number only."
                          )
                        ),
                        settings(effort = Some(effort))
                      )
                      .map { r =>
                        check(r.contentHead.contains("10"), s"wrong answer '${r.contentHead}'")
                        s"'${r.contentHead}', completion tokens " +
                          s"${r.usage.flatMap(_.completion_tokens)}"
                      }
                  }
                }
            }

          _ <- section(s"$model: reasoning_effort=minimal -> low") {
            anthropic
              .createChatCompletion(
                Seq(UserMessage("Say ok.")),
                settings(effort = Some(ReasoningEffort.minimal))
              )
              .map(r => s"'${r.contentHead}'")
          }

          _ <- section(
            s"$model: typed stream, reasoning_effort=high - thinking + signatures"
          ) {
            anthropic
              .createChatCompletionStreamedTyped(
                Seq(UserMessage(batAndBall)),
                settings(maxTokens = 8000, effort = Some(ReasoningEffort.high))
              )
              .assembled
              .map { a =>
                check(a.thinking.nonEmpty, "no thinking")
                check(a.thinkingSignatures.nonEmpty, "no thinking signature")
                check(
                  a.text.contains("0.05") || a.text.contains("5 cent"),
                  s"wrong '${a.text}'"
                )
                s"thinking ${a.thinking.length} chars, ${a.thinkingSignatures.size} signature(s), " +
                  s"'${a.text.take(80)}'"
              }
          }

          _ <- section(s"$model: a thinking budget -> adaptive thinking") {
            anthropic
              .createChatCompletion(
                Seq(UserMessage("What is 12*12? Number only.")),
                settings().setAnthropicThinkingBudgetTokens(2000)
              )
              .map { r =>
                check(r.contentHead.contains("144"), s"wrong '${r.contentHead}'")
                s"'${r.contentHead}'"
              }
          }

          _ <- section(
            s"$model: the OpenAI-shaped stream, reasoning_effort=high - reasoning_content"
          ) {
            anthropic
              .createChatCompletionStreamed(
                Seq(UserMessage(batAndBall)),
                settings(maxTokens = 8000, effort = Some(ReasoningEffort.high))
              )
              .runWith(Sink.seq)
              .map { chunks =>
                val (text, reasoning, finish) = streamedText(chunks)
                check(reasoning.nonEmpty, "no reasoning_content streamed")
                check(text.contains("0.05") || text.contains("5 cent"), s"wrong '$text'")
                s"reasoning ${reasoning.length} chars, text '${text.take(80)}', finish $finish"
              }
          }
        } yield ()
      }

      _ <- group("other") {
        for {
          _ <- section(s"$model: the OpenAI-shaped stream") {
            anthropic
              .createChatCompletionStreamed(
                Seq(UserMessage("Name three primary colours, comma-separated.")),
                settings(maxTokens = 1000, effort = Some(ReasoningEffort.none))
              )
              .runWith(Sink.seq)
              .map { chunks =>
                val (text, _, finish) = streamedText(chunks)
                check(text.toLowerCase.contains("red"), s"text '$text'")
                s"${chunks.size} chunks, finish $finish, usage ${chunks.flatMap(_.usage).lastOption.map(_.total_tokens)}, '$text'"
              }
          }

          _ <- section(s"$model: an image") {
            val dataUrl =
              "data:image/png;base64," + Base64.getEncoder.encodeToString(png(Color.BLUE))
            anthropic
              .createChatCompletion(
                Seq(
                  UserSeqMessage(
                    Seq(
                      TextContent("Which colour is this square? One word."),
                      ImageURLContent(dataUrl)
                    )
                  )
                ),
                settings(maxTokens = 1000, effort = Some(ReasoningEffort.none))
              )
              .map { r =>
                check(r.contentHead.toLowerCase.contains("blue"), s"saw '${r.contentHead}'")
                s"'${r.contentHead}'"
              }
          }

          _ <- section(
            s"$model: prompt caching - the second call reads the cached system prompt"
          ) {
            val cached = settings(maxTokens = 300, effort = Some(ReasoningEffort.none))
              .setUseAnthropicSystemMessagesCache(true)
            def ask(question: String) =
              anthropic.createChatCompletion(
                Seq(SystemMessage(longPolicy), UserMessage(question)),
                cached
              )
            for {
              first <- ask("Which rule covers order 42? Answer with the rule number only.")
              second <- ask("Which rule covers order 7? Answer with the rule number only.")
            } yield {
              val read = second.usage.flatMap(_.prompt_tokens_details).map(_.cached_tokens)
              check(read.exists(_ > 0), s"nothing read from the cache: ${second.usage}")
              s"first prompt ${first.usage.map(_.prompt_tokens)} tokens, second read $read " +
                s"cached of ${second.usage.map(_.prompt_tokens)}"
            }
          }

          _ <- section(s"$model: Anthropic's web search tool on the typed stream") {
            val search = anthropic
              .createChatToolCompletionStreamed(
                Seq(
                  UserMessage(
                    "Search the web: what is the latest stable release of the Scala 3 compiler? Answer briefly."
                  )
                ),
                Nil,
                None,
                settings(effort = Some(ReasoningEffort.low))
                  .setAnthropicTools(Seq(Tool.webSearch(maxUses = Some(1))))
              )
              .assembled
              .map { a =>
                check(
                  a.webSearches.nonEmpty || a.toolCalls.exists(_.serverSide),
                  s"no web search: ${a.toolCalls}"
                )
                check(a.text.nonEmpty, "no answer")
                s"searches ${a.webSearches.flatMap(_.queries)}, results " +
                  s"${a.webSearchResults.map(_.results.size).sum}, citations ${a.citations.size}, '${a.text
                      .take(80)}'"
              }
            // Bedrock may not serve Anthropic's server tools - report it instead of failing
            if (onBedrock)
              search.recover { case e: OpenAIScalaClientException =>
                s"refused by Bedrock: ${e.getClass.getSimpleName}: ${e.getMessage.take(160)}"
              }
            else search
          }
        } yield ()
      }

      _ <- group("bedrock") {
        // the bearer-token and the SigV4 services, each only when its keys are set
        lazy val bedrock =
          AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(region = "eu-central-1")
        lazy val bedrockSigV4 = AnthropicServiceFactory.bedrockAsOpenAI()
        val bearer = "AWS_BEARER_TOKEN_BEDROCK"

        (for {
          _ <- section(s"$bedrockModel: JSON (json_schema), a Bedrock API key") {
            withBedrock(bearer) {
              bedrock
                .createChatCompletionWithJSON[Capital](
                  Seq(UserMessage("Capital of Norway?")),
                  settings(maxTokens = 2000, modelId = bedrockModel).copy(
                    response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
                    jsonSchema = Some(capitalSchema)
                  )
                )
                .map(c => s"parsed=$c")
            }
          }

          _ <- section(s"$bedrockModel: JSON (case-class schema), SigV4 keys") {
            withBedrock("AWS_BEDROCK_ACCESS_KEY") {
              bedrockSigV4
                .createChatCompletionWithJSON[Order](
                  orderMessage,
                  settings(modelId = bedrockModel).copy(jsonSchema = Some(orderSchema))
                )
                .map { order =>
                  check(order.items.size == 2, s"items ${order.items}")
                  s"parsed=$order"
                }
            }
          }

          _ <- section(s"$bedrockModel: a tool loop, default thinking") {
            withBedrock(bearer) {
              toolLoop(bedrock, weatherQuestion, settings(modelId = bedrockModel), Seq("18"))
            }
          }

          _ <- section(s"$bedrockModel: typed stream, reasoning_effort=high") {
            withBedrock(bearer) {
              bedrock
                .createChatCompletionStreamedTyped(
                  Seq(UserMessage("Is 391 prime? One sentence.")),
                  settings(
                    maxTokens = 8000,
                    effort = Some(ReasoningEffort.high),
                    modelId = bedrockModel
                  )
                )
                .assembled
                .map { a =>
                  check(a.text.nonEmpty, "no text")
                  s"thinking ${a.thinking.length} chars, ${a.thinkingSignatures.size} signature(s), '${a.text
                      .take(80)}'"
                }
            }
          }

          _ <- section(
            s"eu.${NonOpenAIModelId.bedrock_claude_haiku_5_5} from eu-central-1, us. from us-east-1"
          ) {
            withBedrock(bearer) {
              def ask(
                region: String,
                profile: String
              ) = {
                val regional =
                  AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(region = region)
                regional
                  .createChatCompletion(
                    Seq(UserMessage("Say ok.")),
                    settings(
                      maxTokens = 200,
                      modelId = s"$profile.${NonOpenAIModelId.bedrock_claude_haiku_5_5}"
                    )
                  )
                  .map(r => s"$profile from $region: '${r.contentHead}'")
                  .andThen { case _ => regional.close() }
              }
              for {
                eu <- ask("eu-central-1", "eu")
                us <- ask("us-east-1", "us")
              } yield s"$eu; $us"
            }
          }

          _ <- section("inferenceProfilePrefix = global + the bare id, JSON") {
            withBedrock(bearer) {
              val prefixed = AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(
                region = "eu-central-1",
                inferenceProfilePrefix = Some("global")
              )
              prefixed
                .createChatCompletionWithJSON[Capital](
                  Seq(UserMessage("Capital of Portugal?")),
                  settings(modelId = NonOpenAIModelId.bedrock_claude_haiku_5_5)
                    .copy(jsonSchema = Some(capitalSchema))
                )
                .map { c =>
                  check(c.capital.contains("Lisbon"), s"wrong capital $c")
                  s"parsed=$c"
                }
                .andThen { case _ => prefixed.close() }
            }
          }

          _ <- section("the bare id without a profile - a classified 400") {
            withBedrock(bearer) {
              bedrock
                .createChatCompletion(
                  Seq(UserMessage("Say ok.")),
                  settings(
                    maxTokens = 200,
                    modelId = NonOpenAIModelId.bedrock_claude_haiku_5_5
                  )
                )
                .map(r => s"answered?! '${r.contentHead}'")
                .recover { case e: OpenAIScalaClientException =>
                  s"refused: ${e.getClass.getSimpleName}: ${e.getMessage.take(160)}"
                }
                .map { msg =>
                  check(!msg.startsWith("answered"), msg)
                  msg
                }
            }
          }

          _ <- section(s"$bedrockModel: an MCP server (Bedrock has no MCP connector)") {
            withBedrock(bearer) {
              bedrock
                .createChatToolCompletion(
                  mcpQuestion,
                  Seq(deepwiki),
                  settings = settings(modelId = bedrockModel)
                )
                .map(r =>
                  s"answered?! '${r.choices.head.message.content.getOrElse("").take(80)}'"
                )
                .recover { case e: OpenAIScalaClientException =>
                  s"refused: ${e.getClass.getSimpleName}: ${e.getMessage.take(160)}"
                }
            }
          }
        } yield ()).andThen { case _ =>
          if (sys.env.get(bearer).exists(_.trim.nonEmpty)) bedrock.close()
          if (sys.env.get("AWS_BEDROCK_ACCESS_KEY").exists(_.trim.nonEmpty))
            bedrockSigV4.close()
        }
      }
    } yield ()

    // a run that fails or times out before its last section is a failure too
    Try(Await.result(all, 30.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: ${describe(e)}")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    anthropic.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
