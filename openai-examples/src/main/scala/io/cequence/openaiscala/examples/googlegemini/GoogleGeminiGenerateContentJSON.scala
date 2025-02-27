package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.{Content, Schema, SchemaType}
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

// requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY` environment variable to be set
object GoogleGeminiGenerateContentJSON extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  private val systemPrompt: Content =
    Content.textPart("You are an expert geographer", User)

  private val contents: Seq[Content] = Seq(
    Content.textPart("List all Asian countries in the prescribed JSON format.", User)
  )

  private val jsonSchema = Schema(
    SchemaType.OBJECT,
    properties = Some(
      Map(
        "countries" -> Schema(
          SchemaType.ARRAY,
          items = Some(
            Schema(
              SchemaType.OBJECT,
              properties = Some(
                Map(
                  "country" -> Schema(SchemaType.STRING),
                  "capital" -> Schema(SchemaType.STRING),
                  "countrySize" -> Schema(
                    SchemaType.STRING,
                    `enum` = Some(Seq("small", "medium", "large"))
                  ),
                  "commonwealthMember" -> Schema(SchemaType.BOOLEAN),
                  "populationMil" -> Schema(SchemaType.INTEGER),
                  "ratioOfMenToWomen" -> Schema(SchemaType.NUMBER)
                )
              ),
              required = Some(
                Seq(
                  "country",
                  "capital",
                  "countrySize",
                  "commonwealthMember",
                  "populationMil",
                  "ratioOfMenToWomen"
                )
              )
            )
          )
        )
      )
    ),
    required = Some(Seq("countries"))
  )

  override protected def run: Future[_] =
    service
      .generateContent(
        contents,
        settings = GenerateContentSettings(
          model = NonOpenAIModelId.gemini_pro_experimental,
          systemInstruction = Some(systemPrompt),
          generationConfig = Some(
            GenerationConfig(
              maxOutputTokens = Some(4000),
              temperature = Some(0.2),
              responseMimeType = Some("application/json"),
              responseSchema = Some(jsonSchema)
            )
          )
        )
      )
      .map { response =>
        println(response.contentHeadText)
      }
}
