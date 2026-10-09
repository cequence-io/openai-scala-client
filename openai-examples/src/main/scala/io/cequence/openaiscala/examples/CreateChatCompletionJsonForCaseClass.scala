package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.service.JsonSchemaReflectionHelper
import play.api.libs.json.{Format, Json}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

import scala.concurrent.Future

// the JSON schema is derived from the case classes - by runtime reflection on Scala 2, by a
// macro on Scala 3 (the same call and schema on both)
object CreateChatCompletionJsonForCaseClass extends Example with JsonSchemaReflectionHelper {

  // data model
  case class Country(
    country: String,
    @JsonSchemaDescription("The capital city") capital: String,
    @JsonSchemaDescription("The population in millions") populationMil: Int,
    ratioOfMenToWomen: Double
  )

  @JsonSchemaDescription("The countries, most populous first")
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
          model = ModelId.gpt_5_4_mini,
          jsonSchema = Some(jsonSchema)
        )
      )
      .map { response =>
        response.capitals.foreach(println)
      }
  }
}
