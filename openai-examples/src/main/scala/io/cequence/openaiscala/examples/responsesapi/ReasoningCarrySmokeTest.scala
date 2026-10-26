package io.cequence.openaiscala.examples.responsesapi

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatToolCompletionResponse}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{
  BedrockEndpoint,
  ChatProviderSettings,
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory,
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIServiceFactory
}
import io.cequence.openaiscala.vertexai.domain.settings.CreateChatCompletionSettingsOps.RichVertexAICreateChatCompletionSettings
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for reasoning carried between the rounds of a tool loop: a reply's reasoning
 * comes back as a [[ReasoningMessage]] in its `historyMessages`, and the next round sends it
 * back - to the service that returned it only, in its provider's form.
 *
 *   - `responses`: OpenAI direct (gpt-5.4-mini with effort high, GPT-6 Luna) and Bedrock
 *     runtime (`us.openai.gpt-6.1-sol`, us-east-1) through the Responses API (encrypted
 *     reasoning items), plus a failover in both directions (the other service leaves the
 *     reasoning out - sent, it would be a 400 - and the round is answered), GPT-6 Luna on the
 *     merged streamed service (`OpenAIServiceFactory.withStreaming()`): a sync round 1, then
 *     round 2 as a typed stream, Bedrock's `global.openai.gpt-6.1-sol` at the default effort
 *     and at xhigh (round 1 must reason), and reasoning summaries on sync calls
 *     (`setResponsesReasoningSummary()`, no effort): a plain chat completion (which the flag
 *     sends through the Responses API) and a carry whose items hold summaries, on OpenAI and
 *     Bedrock
 *   - `anthropic`: Claude Haiku 5.5 (signed thinking blocks)
 *   - `gemini`: Gemini 2.5 Flash and 3.8 Flash with thought summaries asked for
 *     (`setGeminiIncludeThoughts()`, no reasoning effort - thought parts)
 *   - `vertex`: Gemini 2.5 Flash on Vertex AI with thoughts asked for
 *     (`setVertexAIIncludeThoughts()`; `VERTEXAI_PROJECT_ID`, optional `VERTEXAI_LOCATION`,
 *     Google application-default credentials)
 *   - `chat`: chat completions providers - DeepSeek (`reasoning_content`), Fireworks gpt-oss
 *     (`reasoning_content`), Mistral Medium (thinking chunks)
 *
 * Each carry section checks that round 2 with the reasoning reads more input tokens than round
 * 2 without it, i.e. that it was sent and read. A model sometimes answers without reasoning -
 * then there is nothing to carry and the section says so. Every section prints PASS/FAIL; the
 * exit code is 1 if any failed. Args: `[responses] [anthropic] [gemini] [vertex] [chat]` (all
 * by default). Requires the keys of the groups run: `OPENAI_SCALA_CLIENT_API_KEY` and a
 * Bedrock API key (`AWS_BEARER_TOKEN_BEDROCK`), `ANTHROPIC_API_KEY`, `GOOGLE_API_KEY`,
 * `DEEPSEEK_API_KEY` / `FIREWORKS_API_KEY` / `MISTRAL_API_KEY`.
 */
object ReasoningCarrySmokeTest {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  // only the typed stream of the merged streamed service needs one
  private implicit val system: ActorSystem = ActorSystem("reasoning-carry-smoke-test")
  private implicit val materializer: Materializer = Materializer(system)

  private val weather = FunctionTool(
    name = "get_weather",
    description = Some("Current weather for a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  // a question that needs some thinking before the tool calls
  private val question = UserMessage(
    "A courier leaves Oslo at 07:40 and drives at 83 km/h; a second leaves Madrid at 09:15 at 97 km/h towards " +
      "each other on a 2,800 km route with a 35-minute stop each. Work out which city each courier is closest to " +
      "when they meet, then call the weather tool for exactly those cities, and answer in one sentence with the " +
      "warmer one."
  )

  // a question to reason about, with no tools - a one-step question gets an empty summary
  private val puzzle = UserMessage(
    "How many positive integers below 10,000 are divisible by 7 or 11 but not by both, and have a digit sum " +
      "divisible by 3? Give only the final number."
  )

  private def toolResults(response: ChatToolCompletionResponse): Seq[ToolMessage] =
    response.assistantToolMessage.tool_calls.collect { case (id, call: FunctionCallSpec) =>
      val celsius =
        if (call.arguments.contains("slo"))
          4
        else
          21
      ToolMessage(Some(s"""{"temp_c": $celsius}"""), id, call.name)
    }

  private def await[T](future: Future[T]): T = Await.result(future, 11.minutes)

  private def promptTokens(response: ChatToolCompletionResponse): Int =
    response.usage.map(_.prompt_tokens).getOrElse(0)

  private def describe(reasoning: ReasoningMessage): String = {
    def chars(value: Option[String]) = value.map(_.length).getOrElse(0)
    val kinds = reasoning.blocks.map { block =>
      val parts = Seq(
        block.field
          .map(field => s"$field text ${chars(block.text)}")
          .orElse(block.text.map(text => s"text ${text.length}")),
        Some(block.summary)
          .filter(_.nonEmpty)
          .map(summary => s"summary ${summary.map(_.length).sum}"),
        block.signature.map(signature => s"signature ${signature.length}"),
        block.encrypted.map(encrypted => s"encrypted ${encrypted.length}")
      ).flatten
      s"block (${parts.mkString(", ")} chars)"
    }
    kinds.mkString(" + ")
  }

  def main(args: Array[String]): Unit = {
    val groups =
      if (args.nonEmpty)
        args.toSet
      else
        Set("responses", "anthropic", "gemini", "vertex", "chat")

    var failures = 0
    def section(name: String)(body: => String): Unit =
      Try(body) match {
        case Success(message) => println(s"PASS $name - $message")
        case Failure(e) =>
          failures += 1
          println(s"FAIL $name - ${e.getClass.getSimpleName}: ${e.getMessage}")
      }

    // round 1, then round 2 with and without the carried reasoning
    def carry(
      service: OpenAIChatCompletionService,
      settings: CreateChatCompletionSettings
    ): (ChatToolCompletionResponse, String) = {
      val first = await(
        service.createChatToolCompletion(Seq(question), Seq(weather), None, settings)
      )
      val results = toolResults(first)
      require(results.nonEmpty, "round 1 made no tool call")

      first.reasoningMessage match {
        case None =>
          (first, "round 1 answered without reasoning - nothing to carry")

        case Some(reasoning) =>
          val withReasoning = await(
            service.createChatToolCompletion(
              Seq(question) ++ first.historyMessages ++ results,
              Seq(weather),
              None,
              settings
            )
          )
          val without = await(
            service.createChatToolCompletion(
              Seq(question, first.assistantToolMessage) ++ results,
              Seq(weather),
              None,
              settings
            )
          )
          require(
            promptTokens(withReasoning) > promptTokens(without),
            s"round 2 read ${promptTokens(withReasoning)} input tokens with the reasoning, ${promptTokens(without)} without - it was not sent"
          )
          (
            first,
            s"${describe(reasoning)}; round 2 read ${promptTokens(withReasoning)} input tokens with it, ${promptTokens(without)} without; answer: ${withReasoning.assistantToolMessage.content.getOrElse("(tool calls)").take(100)}"
          )
      }
    }

    // round 1 sync, then round 2 as a typed stream - with and without the carried reasoning
    def carryIntoStream(
      service: OpenAIChatCompletionService with OpenAIChatCompletionStreamedServiceExtra,
      settings: CreateChatCompletionSettings
    ): String = {
      val first = await(
        service.createChatToolCompletion(Seq(question), Seq(weather), None, settings)
      )
      val results = toolResults(first)
      require(results.nonEmpty, "round 1 made no tool call")

      def streamedInputTokens(messages: Seq[BaseMessage]): Int =
        await(
          service
            .createChatToolCompletionStreamed(messages, Seq(weather), None, settings)
            .runWith(Sink.seq)
        ).collect { case ChatChunk.Usage(usage) => usage.prompt_tokens }.lastOption
          .getOrElse(0)

      first.reasoningMessage match {
        case None =>
          "round 1 answered without reasoning - nothing to carry"

        case Some(reasoning) =>
          val withReasoning = streamedInputTokens(
            Seq(question) ++ first.historyMessages ++ results
          )
          val without = streamedInputTokens(
            Seq(question, first.assistantToolMessage) ++ results
          )
          require(
            withReasoning > without,
            s"streamed round 2 read $withReasoning input tokens with the reasoning, $without without - it was not sent"
          )
          s"${describe(reasoning)}; streamed round 2 read $withReasoning input tokens with it, $without without"
      }
    }

    // round 2 of one service's history on another service
    def failover(
      first: ChatToolCompletionResponse,
      to: OpenAIChatCompletionService,
      settings: CreateChatCompletionSettings
    ): String = {
      val second = await(
        to.createChatToolCompletion(
          Seq(question) ++ first.historyMessages ++ toolResults(first),
          Seq(weather),
          None,
          settings
        )
      )
      s"history with ${first.reasoningMessage.map(describe).getOrElse("no reasoning")} answered: " +
        second.assistantToolMessage.content.getOrElse("(tool calls)").take(100)
    }

    val high = Some(ReasoningEffort.high)
    // Gemini and Mistral think for minutes about the courier question
    val slow = Some(Timeouts(requestTimeout = Some(600000), readTimeout = Some(600000)))

    if (groups.contains("responses")) {
      val openAI = OpenAIServiceFactory()
      val openAIStreamed = OpenAIServiceFactory.withStreaming()
      val bedrock = OpenAIServiceFactory.forBedrock(
        region = "us-east-1",
        endpoint = BedrockEndpoint.Runtime,
        isOpenAIModel = true
      )
      val openAISolSettings =
        CreateChatCompletionSettings(ModelId.gpt_6_1_sol, reasoning_effort = high)
      val bedrockSolSettings =
        CreateChatCompletionSettings(
          "us." + ModelId.bedrock_openai_gpt_6_1_sol,
          reasoning_effort = high
        )

      var openAIFirst: Option[ChatToolCompletionResponse] = None
      var bedrockFirst: Option[ChatToolCompletionResponse] = None

      try {
        section("OpenAI gpt-5.4-mini") {
          val (first, message) = carry(
            openAI,
            CreateChatCompletionSettings(ModelId.gpt_5_4_mini, reasoning_effort = high)
          )
          openAIFirst = Some(first)
          message
        }

        section("OpenAI gpt-6-luna") {
          carry(
            openAI,
            CreateChatCompletionSettings(ModelId.gpt_6_luna, reasoning_effort = high)
          )._2
        }

        section("Bedrock us.openai.gpt-6.1-sol") {
          val (first, message) = carry(bedrock, bedrockSolSettings)
          bedrockFirst = Some(first)
          message
        }

        // the runtime host serves GPT-6 through an inference profile only - the bare
        // `openai.gpt-6.1-sol` is a 400 "on-demand throughput isn't supported" (live 2026-10-26)
        Seq(None, Some(ReasoningEffort.xhigh)).foreach { effort =>
          section(
            s"Bedrock global.openai.gpt-6.1-sol, effort ${effort.getOrElse("default")}"
          ) {
            val (first, message) = carry(
              bedrock,
              CreateChatCompletionSettings(
                "global." + ModelId.bedrock_openai_gpt_6_1_sol,
                reasoning_effort = effort
              )
            )
            require(
              first.reasoningMessage.isDefined,
              s"round 1 brought no reasoning: $message"
            )
            message
          }
        }

        section("failover Bedrock -> OpenAI gpt-6.1-sol") {
          failover(
            bedrockFirst.getOrElse(sys.error("no Bedrock round 1")),
            openAI,
            openAISolSettings
          )
        }

        section("failover OpenAI -> Bedrock us.openai.gpt-6.1-sol") {
          failover(
            openAIFirst.getOrElse(sys.error("no OpenAI round 1")),
            bedrock,
            bedrockSolSettings
          )
        }

        section("OpenAI gpt-6-luna, merged streamed service: sync round 1, streamed round 2") {
          carryIntoStream(
            openAIStreamed,
            CreateChatCompletionSettings(ModelId.gpt_6_luna, reasoning_effort = high)
          )
        }

        section("OpenAI gpt-6-luna, plain chat completion with summaries asked (no effort)") {
          val response = await(
            openAI.createChatCompletion(
              Seq(puzzle),
              CreateChatCompletionSettings(ModelId.gpt_6_luna).setResponsesReasoningSummary()
            )
          )
          // the chat completions API returns no reasoning for OpenAI's models
          val summary = response.reasoningMessage
            .flatMap(_.text)
            .getOrElse(sys.error("no reasoning summary - not answered by the Responses API?"))
          s"${response.reasoningMessage.map(describe).getOrElse("")}: " +
            s"${summary.take(80).replace('\n', ' ')}...; answer: ${response.contentHead}"
        }

        // round 1's items hold summaries now - sent back with the encrypted reasoning
        Seq(
          "OpenAI gpt-6-luna" -> (
            openAI,
            CreateChatCompletionSettings(ModelId.gpt_6_luna)
          ),
          "Bedrock us.openai.gpt-6.1-sol" -> (
            bedrock,
            CreateChatCompletionSettings("us." + ModelId.bedrock_openai_gpt_6_1_sol)
          )
        ).foreach { case (name, (service, settings)) =>
          section(s"$name, summaries asked (no effort), carried") {
            val (first, message) = carry(service, settings.setResponsesReasoningSummary())
            require(
              first.reasoningMessage.forall(_.text.nonEmpty),
              s"round 1 reasoned but summarized nothing: $message"
            )
            message
          }
        }
      } finally {
        openAI.close()
        openAIStreamed.close()
        bedrock.close()
      }
    }

    if (groups.contains("anthropic")) {
      val anthropic = AnthropicServiceFactory.asOpenAI()
      try {
        section("Anthropic claude-haiku-5-5") {
          carry(
            anthropic,
            CreateChatCompletionSettings(
              NonOpenAIModelId.claude_haiku_5_5,
              reasoning_effort = high
            )
          )._2
        }
      } finally {
        anthropic.close()
      }
    }

    if (groups.contains("gemini")) {
      val gemini = GeminiServiceFactory.asOpenAI(timeouts = slow)
      try {
        // no reasoning effort: the models think by default, the flag alone asks for thoughts
        Seq(NonOpenAIModelId.gemini_2_5_flash, NonOpenAIModelId.gemini_3_8_flash).foreach {
          model =>
            section(s"Gemini $model") {
              carry(
                gemini,
                CreateChatCompletionSettings(model).setGeminiIncludeThoughts()
              )._2
            }
        }
      } finally {
        gemini.close()
      }
    }

    if (groups.contains("vertex")) {
      val vertex = VertexAIServiceFactory.asOpenAI()
      try {
        section("Vertex AI gemini-2.5-flash") {
          carry(
            vertex,
            CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash)
              .setVertexAIIncludeThoughts()
          )._2
        }
      } finally {
        vertex.close()
      }
    }

    if (groups.contains("chat")) {
      def chatSection(
        name: String,
        provider: io.cequence.openaiscala.domain.ProviderSettings,
        settings: CreateChatCompletionSettings
      ): Unit = {
        val service = OpenAIChatCompletionServiceFactory(
          provider.coreUrl,
          WsRequestContext(authHeaders =
            Seq("Authorization" -> s"Bearer ${sys.env(provider.apiKeyEnvVariable)}")
          ),
          slow
        )
        try section(name)(carry(service, settings)._2)
        finally service.close()
      }

      chatSection(
        "DeepSeek deepseek-reasoner",
        ChatProviderSettings.deepseek,
        CreateChatCompletionSettings("deepseek-reasoner")
      )
      chatSection(
        "Fireworks gpt-oss-120b",
        ChatProviderSettings.fireworks,
        CreateChatCompletionSettings("accounts/fireworks/models/gpt-oss-120b")
      )
      chatSection(
        "Mistral mistral-medium-latest",
        ChatProviderSettings.mistral,
        CreateChatCompletionSettings("mistral-medium-latest", reasoning_effort = high)
      )
    }

    val passed = failures == 0
    val summary =
      if (passed)
        "ALL PASSED"
      else
        s"$failures FAILED"
    println(s"\n$summary")
    system.terminate()
    System.exit(
      if (passed)
        0
      else
        1
    )
  }
}
