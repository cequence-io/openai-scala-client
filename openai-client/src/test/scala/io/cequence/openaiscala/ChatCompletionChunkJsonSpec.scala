package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.{ChatCompletionChunkResponse, ChunkMessageSpec}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json._

import java.{util => ju}

class ChatCompletionChunkJsonSpec extends Matchers with AnyWordSpecLike {

  "ChatCompletionChunkResponse" should {
    "parse a real streaming chunk with a tool-call delta (first fragment)" in {
      val json = Json.parse("""
        |{
        |  "id": "chatcmpl-1",
        |  "object": "chat.completion.chunk",
        |  "created": 1700000000,
        |  "model": "gpt-5.4-mini",
        |  "system_fingerprint": null,
        |  "choices": [
        |    {
        |      "index": 0,
        |      "delta": {
        |        "role": "assistant",
        |        "content": null,
        |        "tool_calls": [
        |          {
        |            "index": 0,
        |            "id": "call_abc",
        |            "type": "function",
        |            "function": { "name": "get_weather", "arguments": "" }
        |          }
        |        ]
        |      },
        |      "finish_reason": null
        |    }
        |  ],
        |  "usage": null
        |}
        |""".stripMargin)

      val response = json.as[ChatCompletionChunkResponse]

      response.id shouldBe "chatcmpl-1"
      response.created shouldBe new ju.Date(1700000000000L)
      response.model shouldBe "gpt-5.4-mini"
      response.system_fingerprint shouldBe None
      response.usage shouldBe None

      val choice = response.choices.head
      choice.index shouldBe 0
      choice.finish_reason shouldBe None

      val delta = choice.delta
      delta.role shouldBe Some(ChatRole.Assistant)
      delta.content shouldBe None
      delta.tool_calls shouldBe Some(
        Seq(
          ToolCallChunkSpec(
            index = 0,
            id = Some("call_abc"),
            `type` = Some("function"),
            function = Some(FunctionCallChunkSpec(Some("get_weather"), Some("")))
          )
        )
      )
    }

    "parse a follow-up chunk with only an arguments fragment" in {
      val json = Json.parse("""
        |{
        |  "id": "chatcmpl-1",
        |  "object": "chat.completion.chunk",
        |  "created": 1700000000,
        |  "model": "gpt-5.4-mini",
        |  "system_fingerprint": null,
        |  "choices": [
        |    {
        |      "index": 0,
        |      "delta": {
        |        "tool_calls": [
        |          { "index": 0, "function": { "arguments": "{\"city\":" } }
        |        ]
        |      },
        |      "finish_reason": null
        |    }
        |  ],
        |  "usage": null
        |}
        |""".stripMargin)

      val response = json.as[ChatCompletionChunkResponse]
      val delta = response.choices.head.delta

      delta.role shouldBe None
      delta.content shouldBe None
      delta.tool_calls shouldBe Some(
        Seq(
          ToolCallChunkSpec(
            index = 0,
            id = None,
            `type` = None,
            function = Some(FunctionCallChunkSpec(None, Some("{\"city\":")))
          )
        )
      )
    }

    "parse a plain text chunk" in {
      val json = Json.parse("""
        |{
        |  "id": "chatcmpl-1",
        |  "object": "chat.completion.chunk",
        |  "created": 1700000000,
        |  "model": "gpt-5.4-mini",
        |  "system_fingerprint": null,
        |  "choices": [
        |    {
        |      "index": 0,
        |      "delta": { "content": "Hi" },
        |      "finish_reason": null
        |    }
        |  ],
        |  "usage": null
        |}
        |""".stripMargin)

      val response = json.as[ChatCompletionChunkResponse]
      val delta = response.choices.head.delta

      delta.tool_calls shouldBe None
      delta.content shouldBe Some("Hi")
    }

    "parse a final chunk with an empty delta and a finish reason" in {
      val json = Json.parse("""
        |{
        |  "id": "chatcmpl-1",
        |  "object": "chat.completion.chunk",
        |  "created": 1700000000,
        |  "model": "gpt-5.4-mini",
        |  "system_fingerprint": null,
        |  "choices": [
        |    {
        |      "index": 0,
        |      "delta": {},
        |      "finish_reason": "stop"
        |    }
        |  ],
        |  "usage": null
        |}
        |""".stripMargin)

      val response = json.as[ChatCompletionChunkResponse]
      val choice = response.choices.head

      choice.delta.role shouldBe None
      choice.delta.content shouldBe None
      choice.delta.tool_calls shouldBe None
      choice.finish_reason shouldBe Some("stop")
    }
  }

  "ChunkMessageSpec" should {
    "serialize with tool_calls and without a content key" in {
      val spec = ChunkMessageSpec(
        None,
        None,
        Some(
          Seq(
            ToolCallChunkSpec(
              index = 1,
              id = Some("id"),
              `type` = Some("function"),
              function = Some(FunctionCallChunkSpec(Some("f"), Some("{}")))
            )
          )
        )
      )

      val json = Json.toJson(spec).as[JsObject]

      json.keys shouldNot contain("content")
      json.keys should contain("tool_calls")

      val toolCallsJson = (json \ "tool_calls").as[JsArray].value
      toolCallsJson should have size 1

      val toolCallJson = toolCallsJson.head
      (toolCallJson \ "index").as[Int] shouldBe 1
      (toolCallJson \ "id").as[String] shouldBe "id"
      (toolCallJson \ "type").as[String] shouldBe "function"
      (toolCallJson \ "function" \ "name").as[String] shouldBe "f"
      (toolCallJson \ "function" \ "arguments").as[String] shouldBe "{}"
    }

    "serialize without a tool_calls key when there are no tool calls" in {
      val spec = ChunkMessageSpec(None, Some("x"))

      val json = Json.toJson(spec).as[JsObject]

      json.keys shouldNot contain("tool_calls")
      (json \ "content").as[String] shouldBe "x"
    }
  }

  "Assistant message reads (regression guards)" should {
    "read a null-content assistant message as AssistantMessage with empty content" in {
      val json = Json.parse("""{"role":"assistant","content":null}""")

      json.as[BaseMessage] shouldBe AssistantMessage("", None, None)
    }

    "read a null-content assistant message with a function_call as AssistantFunMessage" in {
      val json = Json.parse(
        """{"role":"assistant","content":null,"function_call":{"name":"f","arguments":"{}"}}"""
      )

      json.as[BaseMessage] shouldBe AssistantFunMessage(
        None,
        None,
        Some(FunctionCallSpec("f", "{}"))
      )
    }

    "read an assistant message with content and a function_call as AssistantFunMessage" in {
      val json = Json.parse(
        """{"role":"assistant","content":"hi","function_call":{"name":"f","arguments":"{}"}}"""
      )

      json.as[BaseMessage] shouldBe AssistantFunMessage(
        Some("hi"),
        None,
        Some(FunctionCallSpec("f", "{}"))
      )
    }

    "read a null-content assistant message with tool_calls as AssistantToolMessage" in {
      val json = Json.parse(
        """{"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"f","arguments":"{}"}}]}"""
      )

      json.as[BaseMessage] shouldBe AssistantToolMessage(
        None,
        None,
        Seq(("c1", FunctionCallSpec("f", "{}")))
      )
    }
  }
}
