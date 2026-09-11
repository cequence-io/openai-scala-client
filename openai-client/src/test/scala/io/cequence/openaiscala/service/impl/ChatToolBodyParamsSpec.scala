package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats.chatCompletionToolFormat
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
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
      tools: Seq[FunctionTool],
      choice: Option[String]
    ): Map[String, JsValue] =
      createToolBodyParams(tools, choice).collect { case (param, Some(json)) =>
        param.toString -> json
      }.toMap

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
