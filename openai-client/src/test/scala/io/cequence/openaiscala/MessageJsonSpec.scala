package io.cequence.openaiscala

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.JsonFormats._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, JsArray, JsObject, JsString, Json, Reads, Writes}

class MessageJsonSpec extends Matchers with AnyWordSpecLike {

  private def deserialized(message: BaseMessage) =
    Json.fromJson[BaseMessage](toJsonObject(message)).get

  private def deserializeAs[T: Format](message: T) =
    Json.fromJson[T](toJsonObjectAs(message)).get

  "SystemMessage" should {
    "serialize correctly with content only" in {
      val content = "You are a helpful assistant."

      val message = SystemMessage(content)
      val plainJson = toJsonObject(message)
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys shouldNot contain("name")
      json.size shouldBe 2

      json("role") shouldBe JsString("system")
      json("content") shouldBe JsString(content)

      deserialized(message) shouldBe message
    }

    "serialize and deserialize correctly with content and name" in {
      val content =
        "You are a brilliant mathematician. Take a deep breath and thing step by step."
      val name = "euclid_123"

      val message = SystemMessage(content, name = Some(name))
      val plainJson = toJsonObject(message)
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("name")
      json.size shouldBe 3

      json("role") shouldBe JsString("system")
      json("name") shouldBe JsString(name)
      json("content") shouldBe JsString(content)

      deserialized(message) shouldBe message
    }
  }

  "UserMessage" should {
    "serialize correctly with content only" in {
      val content = "Why does it snow in Norway?"

      val message = UserMessage(content)
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys shouldNot contain("name")
      json.size shouldBe 2

      json("role") shouldBe JsString("user")
      json("content") shouldBe JsString(content)

      deserialized(message) shouldBe message
    }

    "serialize correctly with content and name" in {
      val content = "Why does it snow in Norway? I mean it's really far from South Pole."
      val name = "euclid_123"

      val message = UserMessage(content, name = Some(name))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("name")
      json.size shouldBe 3

      json("role") shouldBe JsString("user")
      json("name") shouldBe JsString(name)
      json("content") shouldBe JsString(content)

      deserialized(message) shouldBe message
    }
  }

  "UserSeqMessage" should {
    "serialize correctly with content only" in {
      val content1 = TextContent("Why does it snow in Norway?")
      val content2 = TextContent("I mean it's really far from South Pole.")
      val content3 = ImageURLContent("https://www.visitnorway.com/")

      val message = UserSeqMessage(Seq(content1, content2, content3))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys shouldNot contain("name")
      json.size shouldBe 2

      json("role") shouldBe JsString("user")
      json("content").asOpt[JsArray] shouldBe defined
      json("content") shouldBe JsArray(
        Seq(
          Json.obj(
            "type" -> "text",
            "text" -> JsString(content1.text)
          ),
          Json.obj(
            "type" -> "text",
            "text" -> JsString(content2.text)
          ),
          Json.obj(
            "type" -> "image_url",
            "image_url" -> Json.obj("url" -> JsString(content3.url))
          )
        )
      )

      deserialized(message) shouldBe message
    }

    "serialize correctly with content and name" in {
      val content1 = TextContent("Why does it snow in Norway?")
      val content2 = TextContent("I mean it's really far from South Pole.")
      val content3 = ImageURLContent("https://www.visitnorway.com/")
      val name = "thor_23"

      val message = UserSeqMessage(Seq(content1, content2, content3), name = Some(name))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("name")
      json.size shouldBe 3

      json("role") shouldBe JsString("user")
      json("name") shouldBe JsString(name)
      json("content").asOpt[JsArray] shouldBe defined
      json("content") shouldBe JsArray(
        Seq(
          Json.obj(
            "type" -> "text",
            "text" -> JsString(content1.text)
          ),
          Json.obj(
            "type" -> "text",
            "text" -> JsString(content2.text)
          ),
          Json.obj(
            "type" -> "image_url",
            "image_url" -> Json.obj("url" -> JsString(content3.url))
          )
        )
      )

      deserialized(message) shouldBe message
    }
  }

  "AssistantMessage" should {
    "serialize correctly with content only" in {
      val content = "Norway is in the northern hemisphere."

      val message = AssistantMessage(content)
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys shouldNot contain("name")
      json.size shouldBe 2

      json("role") shouldBe JsString("assistant")
      json("content") shouldBe JsString(content)

      deserialized(message) shouldBe message
    }

    "serialize correctly with content and name" in {
      val content = "Norway is in the northern hemisphere, which is far from South Pole."
      val name = "euclid_123"

      val message = AssistantMessage(content, name = Some(name))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("name")
      json.size shouldBe 3

      json("role") shouldBe JsString("assistant")
      json("name") shouldBe JsString(name)
      json("content") shouldBe JsString(content)

      deserialized(message) shouldBe message
    }
  }

  "AssistantToolMessage" should {
    "serialize correctly with content only" in {
      val content = "Norway is in the northern hemisphere."

      val message = AssistantToolMessage(Some(content))
      val json = toJson(message)
      val jsonKeys = json.keySet

      println(toJsonObject(message))

      val messages2 = AssistantMessage(content)
      val json2 = toJson(messages2)
      println(toJsonObject(messages2))

//      json shouldNot be(json2)

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys shouldNot contain("name")
      jsonKeys shouldNot contain("tool_calls") // tool_calls must be excluded
      json.size shouldBe 2

      json("role") shouldBe JsString("assistant")
      json("content") shouldBe JsString(content)
    }

    "serialize correctly with content and name" in {
      val content = "Norway is in the northern hemisphere, which is far from South Pole."
      val name = "euclid_123"

      val message = AssistantToolMessage(Some(content), name = Some(name))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("name")
      jsonKeys shouldNot contain("tool_calls") // tool_calls must be excluded
      json.size shouldBe 3

      json("role") shouldBe JsString("assistant")
      json("name") shouldBe JsString(name)
      json("content") shouldBe JsString(content)
    }

    "serialize and deserialize correctly with tool_calls" in {
      val toolId = "get_current_weather_1"
      val toolCallSpec = FunctionCallSpec(
        "get_current_weather",
        arguments = "{\n  \"location\": \"Boston, MA\"\n}"
      )

      val message =
        AssistantToolMessage(tool_calls = Seq(("get_current_weather_1", toolCallSpec)))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys shouldNot contain("content")
      jsonKeys shouldNot contain("name")
      jsonKeys should contain("tool_calls")
      json.size shouldBe 2

      json("role") shouldBe JsString("assistant")
      json("tool_calls").asOpt[JsArray] shouldBe defined
      json("tool_calls") shouldBe JsArray(
        Seq(
          Json.obj(
            "id" -> JsString(toolId),
            "type" -> JsString("function"),
            "function" -> Json.obj(
              "name" -> JsString(toolCallSpec.name),
              "arguments" -> JsString(toolCallSpec.arguments)
            )
          )
        )
      )

      deserialized(message) shouldBe message
    }
  }

  "AssistantFunMessage" should {
    "serialize correctly with content only" in {
      val content = "Norway is in the northern hemisphere."

      val message = AssistantFunMessage(Some(content))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys shouldNot contain("name")
      jsonKeys shouldNot contain("function_call") // function_call must be excluded
      json.size shouldBe 2

      json("role") shouldBe JsString("assistant")
      json("content") shouldBe JsString(content)
    }

    "serialize correctly with content and name" in {
      val content = "Norway is in the northern hemisphere, which is far from South Pole."
      val name = "euclid_123"

      val message = AssistantFunMessage(Some(content), name = Some(name))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("name")
      jsonKeys shouldNot contain("function_call") // function_call must be excluded
      json.size shouldBe 3

      json("role") shouldBe JsString("assistant")
      json("name") shouldBe JsString(name)
      json("content") shouldBe JsString(content)
    }

    "serialize correctly with tool_calls" in {
      val functionCallSpec = FunctionCallSpec(
        "get_current_weather",
        arguments = "{\n  \"location\": \"Boston, MA\"\n}"
      )

      val message = AssistantFunMessage(function_call = Some(functionCallSpec))
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys shouldNot contain("content")
      jsonKeys shouldNot contain("name")
      jsonKeys should contain("function_call")
      json.size shouldBe 2

      json("role") shouldBe JsString("assistant")
      json("function_call").asOpt[JsObject] shouldBe defined
      json("function_call") shouldBe Json.obj(
        "name" -> JsString(functionCallSpec.name),
        "arguments" -> JsString(functionCallSpec.arguments)
      )

      deserialized(message) shouldBe message
    }
  }

  "ToolMessage" should {
    "serialize and deserialize correctly with tool_call_id and (tool/function) name" in {
      val callId = "get_current_weather_1"
      val functionName = "get_current_weather"

      val message = ToolMessage(tool_call_id = callId, name = functionName)
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys shouldNot contain("content")
      jsonKeys should contain("tool_call_id")
      jsonKeys should contain("name")
      json.size shouldBe 3

      json("role") shouldBe JsString("tool")
      json("tool_call_id") shouldBe JsString(callId)
      json("name") shouldBe JsString(functionName)

      deserialized(message) shouldBe message
    }

    "serialize and deserialize correctly with tool_call_id, (tool/function) name, and content (tool/function response)" in {
      val callId = "get_current_weather_1"
      val functionName = "get_current_weather"
      val content = "It's raining in Seattle. It's 50 degrees Fahrenheit."

      val message = ToolMessage(Some(content), tool_call_id = callId, name = functionName)
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("tool_call_id")
      jsonKeys should contain("name")
      json.size shouldBe 4

      json("role") shouldBe JsString("tool")
      json("content") shouldBe JsString(content)
      json("tool_call_id") shouldBe JsString(callId)
      json("name") shouldBe JsString(functionName)

      deserialized(message) shouldBe message
    }
  }

  "FunMessage" should {
    "serialize correctly with content (function response) and (function) name" in {
      val content = "It's raining in Seattle. It's 50 degrees Fahrenheit."
      val functionName = "get_current_weather"

      val message = FunMessage(content, name = functionName)
      val json = toJson(message)
      val jsonKeys = json.keySet

      jsonKeys should contain("role")
      jsonKeys should contain("content")
      jsonKeys should contain("name")
      json.size shouldBe 3

      json("role") shouldBe JsString("function")
      json("content") shouldBe JsString(content)
      json("name") shouldBe JsString(functionName)

      deserialized(message) shouldBe message
    }
  }

  private def toJsonObjectAs[T: Writes](message: T) = Json.toJson(message).as[JsObject]
  private def toJsonObject(message: BaseMessage) = Json.toJson(message).as[JsObject]
  private def toJson(message: BaseMessage) = toJsonObject(message).value
}
