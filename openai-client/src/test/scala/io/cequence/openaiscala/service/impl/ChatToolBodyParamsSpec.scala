package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats.chatCompletionToolFormat
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{ChatCompletionTool, JsonSchema, ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

/**
 * `ChatCompletionBodyMaker.createToolBodyParams` (shared by the sync and the typed streamed
 * tool completion) and `createStreamOptionsParams`.
 */
class ChatToolBodyParamsSpec extends AnyWordSpec with Matchers {

  private object maker extends ChatCompletionBodyMaker {
    def toolParams(
      tools: Seq[ChatCompletionTool],
      choice: Option[String]
    ): Map[String, JsValue] =
      createToolBodyParams(tools, choice).collect { case (param, Some(json)) =>
        param.toString -> json
      }.toMap

    def body(settings: CreateChatCompletionSettings): Map[String, JsValue] =
      createBodyParamsForChatCompletion(
        Seq(UserMessage("hi")),
        settings,
        stream = false
      ).collect { case (param, Some(json)) =>
        param.toString -> json
      }.toMap

    def toolSettings(settings: CreateChatCompletionSettings): CreateChatCompletionSettings =
      settingsForChatToolCompletion(settings)

    def toolsViaResponses(model: String): Boolean = chatToolsRequireResponsesAPI(model)

    def streamOptions(settings: CreateChatCompletionSettings): Map[String, JsValue] =
      createStreamOptionsParams(settings).collect { case (param, Some(json)) =>
        param.toString -> json
      }.toMap
  }

  private val weather = FunctionTool(
    name = "get_weather",
    description = Some("Get the weather"),
    parameters = JsonSchema.Object(properties = Nil)
  )

  "createToolBodyParams" should {

    "emit OpenAI-shaped tools and a forced tool_choice" in {
      val params = maker.toolParams(Seq(weather), Some("get_weather"))

      params("tools") shouldBe Json.arr(
        Json.obj("type" -> "function", "function" -> Json.toJson(weather))
      )
      params("tool_choice") shouldBe Json.obj(
        "type" -> "function",
        "function" -> Json.obj("name" -> "get_weather")
      )
    }

    "omit tool_choice when no tool is forced" in {
      maker.toolParams(Seq(weather), None).keySet shouldBe Set("tools")
    }

    "refuse the provider-neutral tools, which the chat completions API cannot carry" in {
      val e = the[OpenAIScalaClientException] thrownBy maker.toolParams(
        Seq(
          weather,
          ChatCompletionTool.MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp")
        ),
        None
      )
      e.getMessage should include("MCPServerTool(deepwiki)")
      e.getMessage should include("Responses API")

      (the[OpenAIScalaClientException] thrownBy maker.toolParams(
        Seq(ChatCompletionTool.SkillTool("pptx")),
        None
      )).getMessage should include("SkillTool(pptx)")
    }
  }

  "the GPT-6 dispatch" should {

    "keep reasoning_effort 'none' for gpt-6-luna/sol but lift it to 'low' for gpt-6-astra" in {
      val none = CreateChatCompletionSettings(
        model = ModelId.gpt_6_luna,
        max_tokens = Some(100),
        temperature = Some(0.2),
        reasoning_effort = Some(ReasoningEffort.none)
      )

      val luna = maker.body(none)
      luna("reasoning_effort") shouldBe Json.toJson("none")
      luna.get("max_tokens") shouldBe None
      luna("temperature") shouldBe Json.toJson(1d)

      maker.body(none.copy(model = ModelId.gpt_6_sol))("reasoning_effort") shouldBe
        Json.toJson("none")
      maker.body(none.copy(model = "global." + ModelId.bedrock_openai_gpt_6_sol))(
        "reasoning_effort"
      ) shouldBe Json.toJson("none")
      maker.body(none.copy(model = ModelId.gpt_6_astra))("reasoning_effort") shouldBe
        Json.toJson("low")
    }

    "force reasoning_effort 'none' for gpt-6-luna/sol function tools on chat completions" in {
      val high = CreateChatCompletionSettings(
        model = ModelId.gpt_6_luna,
        reasoning_effort = Some(ReasoningEffort.high)
      )

      maker.toolSettings(high).reasoning_effort shouldBe Some(ReasoningEffort.none)
      maker
        .toolSettings(high.copy(model = ModelId.gpt_6_sol, reasoning_effort = None))
        .reasoning_effort shouldBe Some(ReasoningEffort.none)
      maker.toolsViaResponses(ModelId.gpt_6_luna) shouldBe false
      maker.toolsViaResponses(ModelId.gpt_6_astra) shouldBe true
    }
  }

  "createStreamOptionsParams" should {

    "ask for the trailing usage chunk by default" in {
      maker.streamOptions(CreateChatCompletionSettings("gpt-x")) shouldBe
        Map("stream_options" -> Json.obj("include_usage" -> true))
    }

    "leave stream_options alone when the caller set it via extra_params" in {
      val settings = CreateChatCompletionSettings("gpt-x")
        .copy(extra_params = Map("stream_options" -> Map("include_usage" -> false)))

      maker.streamOptions(settings) shouldBe empty
    }
  }
}
