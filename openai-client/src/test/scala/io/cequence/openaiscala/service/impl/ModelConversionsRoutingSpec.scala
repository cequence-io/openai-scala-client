package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  ReasoningEffort,
  Verbosity
}
import io.cequence.openaiscala.domain.{JsonSchema, ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.service.adapter.ChatCompletionSettingsConversions
import io.cequence.openaiscala.service.adapter.ChatCompletionSettingsConversions._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

/**
 * The per-model routing and conversions of `ChatCompletionBodyMaker`, pinned to what the live
 * chat completions API accepts (every rule below was measured 2026-09-26 with
 * `examples/OpenAIConversionsAudit` and raw probes).
 */
class ModelConversionsRoutingSpec extends AnyWordSpec with Matchers {

  private object maker extends ChatCompletionBodyMaker {
    def body(settings: CreateChatCompletionSettings): Map[String, JsValue] =
      createBodyParamsForChatCompletion(
        Seq(SystemMessage("s"), UserMessage("hi")),
        settings,
        stream = false
      ).collect { case (param, Some(json)) => param.toString -> json }.toMap

    def tools(settings: CreateChatCompletionSettings): CreateChatCompletionSettings =
      settingsForChatToolCompletion(settings)
  }

  private def effortSent(
    model: String,
    effort: ReasoningEffort
  ): Option[String] =
    maker
      .body(CreateChatCompletionSettings(model, reasoning_effort = Some(effort)))
      .get("reasoning_effort")
      .map(_.as[String])

  private val weather = FunctionTool(
    name = "get_weather",
    parameters = JsonSchema.Object(properties = Nil)
  )

  "gpt5Minor" should {

    "parse the minor version, with or without a Bedrock prefix" in {
      gpt5Minor("gpt-5") shouldBe Some(0)
      gpt5Minor("gpt-5-mini") shouldBe Some(0)
      gpt5Minor("gpt-5-2025-08-07") shouldBe Some(0)
      gpt5Minor("gpt-5.1") shouldBe Some(1)
      gpt5Minor("gpt-5.4-mini-2026-03-17") shouldBe Some(4)
      gpt5Minor("global.openai.gpt-5.6-luna") shouldBe Some(6)
    }

    "not mistake gpt-5.10 for gpt-5.1, and ignore other families" in {
      gpt5Minor("gpt-5.10") shouldBe Some(10)
      gpt5Minor("gpt-5.4.1-mini") shouldBe Some(4)
      gpt5Minor("gpt-5.6.2") shouldBe Some(6)
      gpt5Minor("gpt-5.12-mini") shouldBe Some(12)
      gpt5Minor("gpt-50") shouldBe None
      gpt5Minor("gpt-6-luna") shouldBe None
      gpt5Minor("gpt-4.1") shouldBe None
    }
  }

  "isOSeries / isO1PreviewOrMini" should {

    "cover the o-series by pattern, incl. ids the old static set missed" in {
      Seq(
        "o1",
        "o1-pro",
        "o3",
        "o3-mini",
        "o3-pro",
        "o3-2025-04-16",
        "o4-mini",
        "us.openai.o3"
      ).foreach(model => withClue(model)(isOSeries(model) shouldBe true))
    }

    "exclude the retired o1-preview / o1-mini and anything else starting with o" in {
      Seq("o1-preview", "o1-mini-2024-09-12", "omni-moderation-latest", "o5", "gpt-4o")
        .foreach(model => withClue(model)(isOSeries(model) shouldBe false))
      isO1PreviewOrMini("o1-mini") shouldBe true
    }
  }

  "reasoning_effort" should {

    "be mapped onto what each family accepts" in {
      // gpt-5 / -mini / -nano: minimal, low, medium, high
      effortSent("gpt-5-mini", ReasoningEffort.none) shouldBe Some("minimal")
      effortSent("gpt-5", ReasoningEffort.xhigh) shouldBe Some("high")
      effortSent("gpt-5-nano", ReasoningEffort.max) shouldBe Some("high")
      effortSent("gpt-5", ReasoningEffort.minimal) shouldBe Some("minimal")
      // 5.1: none, low, medium, high
      effortSent(ModelId.gpt_5_1, ReasoningEffort.minimal) shouldBe Some("low")
      effortSent(ModelId.gpt_5_1, ReasoningEffort.xhigh) shouldBe Some("high")
      effortSent(ModelId.gpt_5_1, ReasoningEffort.max) shouldBe Some("high")
      effortSent(ModelId.gpt_5_1, ReasoningEffort.none) shouldBe Some("none")
      // 5.2 / 5.4 / 5.5: none .. xhigh
      Seq(ModelId.gpt_5_2, ModelId.gpt_5_4_mini, ModelId.gpt_5_5).foreach { model =>
        withClue(model) {
          effortSent(model, ReasoningEffort.minimal) shouldBe Some("low")
          effortSent(model, ReasoningEffort.max) shouldBe Some("xhigh")
          effortSent(model, ReasoningEffort.xhigh) shouldBe Some("xhigh")
          effortSent(model, ReasoningEffort.none) shouldBe Some("none")
        }
      }
      // o-series: low .. xhigh
      Seq("o1", "o3", "o3-mini", "o4-mini").foreach { model =>
        withClue(model) {
          effortSent(model, ReasoningEffort.none) shouldBe Some("low")
          effortSent(model, ReasoningEffort.minimal) shouldBe Some("low")
          effortSent(model, ReasoningEffort.max) shouldBe Some("xhigh")
          effortSent(model, ReasoningEffort.xhigh) shouldBe Some("xhigh")
        }
      }
    }

    "give a future GPT-5 minor the newest (5.6) rules, not the oldest" in {
      effortSent("gpt-5.10", ReasoningEffort.none) shouldBe Some("none")
      effortSent("gpt-5.7-mini", ReasoningEffort.max) shouldBe Some("xhigh")
      maker.body(CreateChatCompletionSettings("gpt-5.7", temperature = Some(0.2)))(
        "temperature"
      ) shouldBe Json.toJson(1d)
    }
  }

  "sampling params" should {

    "be kept on 5.1 / 5.2 without reasoning (incl. 'none') and clamped with it" in {
      Seq(ModelId.gpt_5_1, ModelId.gpt_5_2).foreach { model =>
        withClue(model) {
          val plain = maker.body(
            CreateChatCompletionSettings(
              model,
              temperature = Some(0.2),
              presence_penalty = Some(0.5),
              frequency_penalty = Some(0.5)
            )
          )
          plain("presence_penalty") shouldBe Json.toJson(0.5)
          plain("frequency_penalty") shouldBe Json.toJson(0.5)
          plain("temperature") shouldBe Json.toJson(0.2)

          val none = maker.body(
            CreateChatCompletionSettings(
              model,
              presence_penalty = Some(0.5),
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          none("presence_penalty") shouldBe Json.toJson(0.5)

          val reasoning = maker.body(
            CreateChatCompletionSettings(
              model,
              temperature = Some(0.2),
              presence_penalty = Some(0.5),
              reasoning_effort = Some(ReasoningEffort.low)
            )
          )
          reasoning("presence_penalty") shouldBe Json.toJson(0d)
          reasoning("temperature") shouldBe Json.toJson(1d)
        }
      }
    }

    "keep logprobs on GPT-5.4 without reasoning, except on the two snapshots that 403 it" in {
      def logprobsSent(
        model: String,
        effort: Option[ReasoningEffort]
      ) =
        maker
          .body(
            CreateChatCompletionSettings(
              model,
              logprobs = Some(true),
              reasoning_effort = effort
            )
          )
          .get("logprobs")

      Seq(
        ModelId.gpt_5_4,
        ModelId.gpt_5_4_mini,
        ModelId.gpt_5_4_nano,
        "gpt-5.4-nano-2026-03-17"
      ).foreach { model =>
        withClue(model) {
          logprobsSent(model, None) shouldBe Some(Json.toJson(true))
          logprobsSent(model, Some(ReasoningEffort.none)) shouldBe Some(Json.toJson(true))
          logprobsSent(model, Some(ReasoningEffort.low)) shouldBe None
        }
      }
      Seq(
        "gpt-5.4-2026-03-05",
        "gpt-5.4-mini-2026-03-17",
        "openai.gpt-5.4-2026-03-05"
      ).foreach { model =>
        withClue(model)(logprobsSent(model, None) shouldBe None)
      }
    }

    "drop logprobs on the o-series (a 403 there)" in {
      maker
        .body(CreateChatCompletionSettings("o3", logprobs = Some(true)))
        .get("logprobs") shouldBe
        None
    }
  }

  "gpt-5-search-api" should {

    "drop what it rejects even at the default values, and keep the rest" in {
      val body = maker.body(
        CreateChatCompletionSettings(
          ModelId.gpt_5_search_api,
          max_tokens = Some(300),
          temperature = Some(1),
          top_p = Some(1),
          presence_penalty = Some(0.5),
          frequency_penalty = Some(0.5),
          logprobs = Some(true),
          reasoning_effort = Some(ReasoningEffort.low),
          verbosity = Some(Verbosity.low)
        )
      )

      body.keySet should contain noneOf (
        "temperature",
        "top_p",
        "presence_penalty",
        "frequency_penalty",
        "logprobs",
        "reasoning_effort",
        "verbosity"
      )
      body("model") shouldBe Json.toJson(ModelId.gpt_5_search_api)
      // the dated snapshot too, and it is not treated as plain gpt-5
      maker
        .body(
          CreateChatCompletionSettings("gpt-5-search-api-2025-10-14", temperature = Some(1))
        )
        .get("temperature") shouldBe None
    }
  }

  "chatToolsUnsupported" should {

    "flag gpt-5-search-api (incl. its snapshot) and nothing else" in {
      chatToolsUnsupported(ModelId.gpt_5_search_api) shouldBe true
      chatToolsUnsupported("gpt-5-search-api-2025-10-14") shouldBe true
      chatToolsUnsupported(ModelId.gpt_5_4) shouldBe false
      chatToolsUnsupported("gpt-5") shouldBe false
    }
  }

  "function tools with reasoning" should {

    "drop an explicit effort on 5.4 / 5.5 for chat completions and prefer the Responses API" in {
      Seq(ModelId.gpt_5_4, ModelId.gpt_5_4_nano, ModelId.gpt_5_5).foreach { model =>
        withClue(model) {
          val low =
            CreateChatCompletionSettings(model, reasoning_effort = Some(ReasoningEffort.low))
          maker.tools(low).reasoning_effort shouldBe None
          chatToolsPreferResponsesAPI(low, Seq(weather)) shouldBe true

          // no effort or 'none' works on chat completions - no detour
          val none = low.copy(reasoning_effort = Some(ReasoningEffort.none))
          maker.tools(none).reasoning_effort shouldBe Some(ReasoningEffort.none)
          chatToolsPreferResponsesAPI(none, Seq(weather)) shouldBe false
          chatToolsPreferResponsesAPI(
            low.copy(reasoning_effort = None),
            Seq(weather)
          ) shouldBe false
        }
      }
    }

    "leave older models alone and keep the 5.6+ / GPT-6 rules" in {
      val gpt52 =
        CreateChatCompletionSettings(
          ModelId.gpt_5_2,
          reasoning_effort = Some(ReasoningEffort.low)
        )
      maker.tools(gpt52) shouldBe gpt52
      chatToolsPreferResponsesAPI(gpt52, Seq(weather)) shouldBe false

      val future =
        CreateChatCompletionSettings("gpt-5.8", reasoning_effort = Some(ReasoningEffort.low))
      maker.tools(future).reasoning_effort shouldBe Some(ReasoningEffort.none)
      ChatCompletionSettingsConversions.chatToolsForceNoReasoning("gpt-5.8") shouldBe true
    }
  }
}
