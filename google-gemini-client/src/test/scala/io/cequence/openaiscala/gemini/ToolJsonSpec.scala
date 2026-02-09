package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.Tool.GoogleSearchRetrieval
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.settings.{FunctionCallingMode, ToolConfig}
import io.cequence.openaiscala.gemini.domain._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

class ToolJsonSpec extends AnyWordSpecLike with Matchers {

  private def testCodec[A](
    value: A,
    json: String
  )(
    implicit format: Format[A]
  ): Unit = {
    val serialized = Json.toJson(value).toString()
    Json.parse(serialized).as[A] shouldBe value
    Json.parse(json).as[A] shouldBe value
  }

  "Gemini JSON formats" should {

    "serialize and deserialize FunctionDeclarations tool" in {
      val tool = Tool.FunctionDeclarations(
        Seq(
          FunctionDeclaration(
            name = "get_weather",
            description = "Get current weather for a location.",
            parameters = Some(
              Schema(
                `type` = SchemaType.OBJECT,
                properties = Some(
                  Map(
                    "location" -> Schema(
                      `type` = SchemaType.STRING,
                      description = Some("City, State or country")
                    ),
                    "unit" -> Schema(
                      `type` = SchemaType.STRING,
                      description = Some("Temperature unit"),
                      `enum` = Some(Seq("c", "f"))
                    )
                  )
                ),
                required = Some(Seq("location"))
              )
            )
          )
        )
      )

      val json =
        """{
          |  "functionDeclarations": [
          |    {
          |      "name": "get_weather",
          |      "description": "Get current weather for a location.",
          |      "parameters": {
          |        "type": "OBJECT",
          |        "properties": {
          |          "location": {
          |            "type": "STRING",
          |            "description": "City, State or country"
          |          },
          |          "unit": {
          |            "type": "STRING",
          |            "description": "Temperature unit",
          |            "enum": ["c", "f"]
          |          }
          |        },
          |        "required": ["location"]
          |      }
          |    }
          |  ]
          |}""".stripMargin

      testCodec[Tool](tool, json)
    }

    "serialize and deserialize non-function tools" in {
      testCodec[Tool](Tool.CodeExecution, """{ "codeExecution": {} }""")

      testCodec[Tool](
        GoogleSearchRetrieval(
          DynamicRetrievalConfig(
            mode = DynamicRetrievalPredictorMode.MODE_DYNAMIC,
            dynamicThreshold = 0
          )
        ),
        """{
          |  "googleSearchRetrieval": {
          |    "dynamicRetrievalConfig": {
          |      "mode": "MODE_DYNAMIC",
          |      "dynamicThreshold": 0
          |    }
          |  }
          |}""".stripMargin
      )
    }

    "serialize and deserialize ToolConfig" in {
      val config = ToolConfig.FunctionCallingConfig(
        mode = Some(FunctionCallingMode.ANY),
        allowedFunctionNames = Some(Seq("get_weather", "get_time"))
      )

      val json =
        """{
          |  "functionCallingConfig": {
          |    "mode": "ANY",
          |    "allowedFunctionNames": ["get_weather", "get_time"]
          |  }
          |}""".stripMargin

      testCodec[ToolConfig](config, json)
    }

    "serialize and deserialize functionCall and functionResponse parts" in {
      val functionCall = Part.FunctionCall(
        id = Some("call_1"),
        name = "get_weather",
        args = Map("location" -> "Oslo", "unit" -> "c", "days" -> 1)
      )

      val functionCallJson =
        """{
          |  "functionCall": {
          |    "id": "call_1",
          |    "name": "get_weather",
          |    "args": {
          |      "location": "Oslo",
          |      "unit": "c",
          |      "days": 1
          |    }
          |  }
          |}""".stripMargin

      testCodec[Part](functionCall, functionCallJson)

      val functionResponse = Part.FunctionResponse(
        id = Some("call_1"),
        name = "get_weather",
        response = Map("temperature" -> 5, "unit" -> "c")
      )

      val functionResponseJson =
        """{
          |  "functionResponse": {
          |    "id": "call_1",
          |    "name": "get_weather",
          |    "response": {
          |      "temperature": 5,
          |      "unit": "c"
          |    }
          |  }
          |}""".stripMargin

      testCodec[Part](functionResponse, functionResponseJson)
    }
  }

  "Gemini CreateChatCompletionSettingsOps" should {
    "store and retrieve tools and toolConfig via extra_params" in {
      val tools = Seq(
        Tool.FunctionDeclarations(
          Seq(
            FunctionDeclaration(
              name = "get_time",
              description = "Get current time."
            )
          )
        )
      )
      val toolConfig = ToolConfig.FunctionCallingConfig(
        mode = Some(FunctionCallingMode.AUTO),
        allowedFunctionNames = None
      )

      val settings = CreateChatCompletionSettings(model = "gemini-test")
        .setGeminiTools(tools)
        .setGeminiToolConfig(toolConfig)

      settings.getGeminiTools shouldBe Some(tools)
      settings.getGeminiToolConfig shouldBe Some(toolConfig)
    }
  }
}
