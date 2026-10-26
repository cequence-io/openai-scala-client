package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.responsesapi.ReasoningConfig
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{JsonSchema, ModelId}
import io.cequence.openaiscala.service.OpenAIResponsesService
import io.cequence.wsclient.service.CloseableService
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Tool completions of the reasoning-first OpenAI models (GPT-5.6, GPT-6) and the Responses API
 * route that keeps their reasoning with tools (all rules live-verified 2026-09-22).
 */
class ReasoningModelsResponsesRoutingSpec extends AnyWordSpec with Matchers {

  private val weather = FunctionTool(
    name = "get_weather",
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  "chatToolsPreferResponsesAPI" should {

    "prefer the Responses API for GPT-5.6 / GPT-6 Sol/Luna tools unless reasoning is 'none'" in {
      Seq(
        ModelId.gpt_6_luna,
        ModelId.gpt_6_sol,
        ModelId.gpt_5_6_sol,
        "global.openai.gpt-6-luna"
      ).foreach { model =>
        val unset = CreateChatCompletionSettings(model)
        ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(
          unset,
          Seq(weather)
        ) shouldBe true
        ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(
          unset.copy(reasoning_effort = Some(ReasoningEffort.high)),
          Seq(weather)
        ) shouldBe true
        ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(
          unset.copy(reasoning_effort = Some(ReasoningEffort.none)),
          Seq(weather)
        ) shouldBe false
      }
    }

    "always route GPT-6 Astra tools, and never a call without tools or an older model" in {
      ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(
        CreateChatCompletionSettings(
          ModelId.gpt_6_astra,
          reasoning_effort = Some(ReasoningEffort.none)
        ),
        Seq(weather)
      ) shouldBe true
      ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(
        CreateChatCompletionSettings(ModelId.gpt_6_luna),
        Nil
      ) shouldBe false
      ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(
        CreateChatCompletionSettings(ModelId.gpt_5_5),
        Seq(weather)
      ) shouldBe false
    }
  }

  "isOpenAIReasoningModel" should {

    "know OpenAI's reasoning models, also by a Bedrock id, but not gpt-5-search-api" in {
      Seq(
        ModelId.gpt_6_luna,
        ModelId.gpt_6_1_sol,
        ModelId.gpt_5_4_mini,
        ModelId.gpt_5_mini,
        ModelId.o3,
        "us.openai.gpt-6.1-sol"
      ).foreach { model =>
        withClue(model) {
          ChatCompletionSettingsConversions.isOpenAIReasoningModel(model) shouldBe true
        }
      }

      Seq(
        ModelId.gpt_4_1_mini,
        ModelId.gpt_5_search_api,
        ModelId.chat_latest,
        "openai/gpt-5.4",
        "claude-haiku-5-5"
      ).foreach { model =>
        withClue(model) {
          ChatCompletionSettingsConversions.isOpenAIReasoningModel(model) shouldBe false
        }
      }
    }
  }

  "chatRequiresResponsesAPI" should {

    "take an OpenAI reasoning model asked for reasoning summaries, with or without tools" in {
      val summarized =
        CreateChatCompletionSettings(ModelId.gpt_6_luna).setResponsesReasoningSummary()

      ChatCompletionSettingsConversions.chatRequiresResponsesAPI(summarized) shouldBe true
      ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(summarized, Nil) shouldBe
        true
      // with 'none', the chat completions API would serve the tools - not the summaries
      ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(
        summarized.copy(reasoning_effort = Some(ReasoningEffort.none)),
        Seq(weather)
      ) shouldBe true
      ChatCompletionSettingsConversions.chatRequiresResponsesAPI(
        CreateChatCompletionSettings("us.openai.gpt-6.1-sol").setResponsesReasoningSummary()
      ) shouldBe true
    }

    "leave the rest to the chat completions API" in {
      Seq(
        CreateChatCompletionSettings(ModelId.gpt_6_luna),
        CreateChatCompletionSettings(ModelId.gpt_6_luna).setResponsesReasoningSummary(false),
        CreateChatCompletionSettings(ModelId.gpt_4_1_mini).setResponsesReasoningSummary(),
        CreateChatCompletionSettings(ModelId.gpt_5_search_api).setResponsesReasoningSummary()
      ).foreach { settings =>
        withClue(settings) {
          ChatCompletionSettingsConversions.chatRequiresResponsesAPI(settings) shouldBe false
        }
      }
    }
  }

  "toResponsesSettings" should {

    // settings mapping only - the underlying service is never called
    val adapter = OpenAIResponsesChatCompletionService(
      null.asInstanceOf[OpenAIResponsesService with CloseableService]
    )

    "drop temperature / top_p / top_logprobs and keep 'max' for GPT-6 Luna" in {
      val out = adapter.toResponsesSettings(
        CreateChatCompletionSettings(
          model = ModelId.gpt_6_luna,
          temperature = Some(0.2),
          top_p = Some(0.5),
          top_logprobs = Some(2),
          reasoning_effort = Some(ReasoningEffort.max)
        ),
        None,
        Nil,
        None
      )

      out.temperature shouldBe None
      out.topP shouldBe None
      out.topLogprobs shouldBe None
      out.reasoning shouldBe Some(ReasoningConfig(effort = Some(ReasoningEffort.max)))
    }

    "downgrade 'minimal' for GPT-6 and 'none' for Astra, and keep sampling params elsewhere" in {
      adapter
        .toResponsesSettings(
          CreateChatCompletionSettings(
            ModelId.gpt_6_sol,
            reasoning_effort = Some(ReasoningEffort.minimal)
          ),
          None,
          Nil,
          None
        )
        .reasoning shouldBe Some(ReasoningConfig(effort = Some(ReasoningEffort.low)))

      adapter
        .toResponsesSettings(
          CreateChatCompletionSettings(
            ModelId.gpt_6_astra,
            reasoning_effort = Some(ReasoningEffort.none)
          ),
          None,
          Nil,
          None
        )
        .reasoning shouldBe Some(ReasoningConfig(effort = Some(ReasoningEffort.low)))

      adapter
        .toResponsesSettings(
          CreateChatCompletionSettings(ModelId.gpt_4_1, temperature = Some(0.2)),
          None,
          Nil,
          None
        )
        .temperature shouldBe Some(0.2)
    }
  }
}
