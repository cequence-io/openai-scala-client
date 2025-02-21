package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.service.JsonSchemaReflectionHelper
import play.api.libs.json.{Format, Json}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

import scala.concurrent.Future

// due to the reflection used in jsonSchemaFor, this example currently works only for Scala 2.12 and 2.13
object CreateChatCompletionJsonForCaseClass extends Example with JsonSchemaReflectionHelper {

  // data model
  case class Country(
    country: String,
    capital: String,
    populationMil: Int,
    ratioOfMenToWomen: Double
  )

  case class CapitalsResponse(capitals: Seq[Country])

  // JSON format and schema
  implicit val countryFormat: Format[Country] = Json.format[Country]
  implicit val capitalsResponseFormat: Format[CapitalsResponse] = Json.format[CapitalsResponse]

  val jsonSchema: JsonSchemaDef = JsonSchemaDef(
    name = "capitals_response",
    strict = true,
    jsonSchemaFor[CapitalsResponse]()
  )

  // messages / prompts
  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are an expert geographer"),
    UserMessage("List the most populous African countries in the prescribed JSON format")
  )

  override protected def run: Future[_] = {
    // chat completion JSON run
    service
      .createChatCompletionWithJSON[CapitalsResponse](
        messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_4o_2024_08_06,
          temperature = Some(0),
          jsonSchema = Some(jsonSchema)
        )
      )
      .map { response =>
        response.capitals.foreach(println)
      }
  }
}
