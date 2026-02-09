package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.{
  Content,
  FunctionDeclaration,
  Part,
  Schema,
  SchemaType,
  Tool
}
import io.cequence.openaiscala.gemini.domain.settings.{
  FunctionCallingMode,
  GenerateContentSettings,
  GenerationConfig,
  ToolConfig
}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

/**
 * Example showing function calling with Gemini using native Gemini tool format.
 *
 * Requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY`
 * environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionWithToolsFunctionDeclarations
    extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  private val tools = Seq(
    Tool.FunctionDeclarations(
      Seq(
        FunctionDeclaration(
          name = "get_weather",
          description = "Get the current weather for a location.",
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
  )

  private val toolConfig = ToolConfig.FunctionCallingConfig(
    mode = Some(FunctionCallingMode.ANY),
    allowedFunctionNames = Some(Seq("get_weather"))
  )

  private val contents: Seq[Content] = Seq(
    Content.textPart("What is the weather in Oslo right now?", User)
  )

  override protected def run: Future[_] =
    service
      .generateContent(
        contents,
        settings = GenerateContentSettings(
          model = NonOpenAIModelId.gemini_2_5_flash,
          tools = Some(tools),
          toolConfig = Some(toolConfig),
          generationConfig = Some(
            GenerationConfig(
              maxOutputTokens = Some(2000),
              temperature = Some(0.7)
            )
          )
        )
      )
      .map { response =>
        println("Response:")
        println(response.contentHeadText)

        val functionCalls = response.candidates.flatMap(_.content.parts.collect {
          case call: Part.FunctionCall => call
        })

        if (functionCalls.nonEmpty) {
          println("\nFunction calls:")
          functionCalls.foreach { call =>
            println(s"  Id: ${call.id.getOrElse("N/A")}")
            println(s"  Function: ${call.name}")
            println(s"  Arguments: ${call.args}")
          }
        }

        val functionResponses = response.candidates.flatMap(_.content.parts.collect {
          case resp: Part.FunctionResponse => resp
        })

        if (functionResponses.nonEmpty) {
          println("\nFunction responses:")
          functionResponses.foreach { resp =>
            println(s"  Id: ${resp.id.getOrElse("N/A")}")
            println(s"  Function: ${resp.name}")
            println(s"  Response: ${resp.response}")
          }
        }
      }
}
