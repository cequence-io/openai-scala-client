package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.examples.fixtures.TestFixtures
import io.cequence.openaiscala.service.{JsonSchemaReflectionHelper, OpenAIServiceConsts}
import play.api.libs.json.Json

import scala.concurrent.Future

// experimental
object CreateChatCompletionJsonForCaseClass
    extends Example
    with TestFixtures
    with JsonSchemaReflectionHelper
    with OpenAIServiceConsts {

  private val messages = Seq(
    SystemMessage(capitalsPrompt),
    UserMessage("List only african countries")
  )

  // Case class(es)
  private case class CapitalsResponse(
    countries: Seq[Country]
  )

  private case class Country(
    country: String,
    capital: String
  )

  // json schema def
  private val jsonSchemaDef: JsonSchemaDef = JsonSchemaDef(
    name = "capitals_response",
    strict = true,
    // reflective json schema for case class
    structure = jsonSchemaFor[CapitalsResponse]()
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = DefaultSettings.createJsonChatCompletion(jsonSchemaDef)
      )
      .map { response =>
        val json = Json.parse(messageContent(response))
        println(Json.prettyPrint(json))
      }
}
