package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.examples.fixtures.TestFixtures
import io.cequence.openaiscala.service.OpenAIServiceConsts
import play.api.libs.json.{JsObject, Json}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

import scala.concurrent.Future

object CreateChatCompletionJsonWithO3Mini
    extends Example
    with TestFixtures
    with OpenAIServiceConsts {

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage(capitalsPrompt),
    UserMessage("List only african countries")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.o3_mini,
          max_tokens = Some(5000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(capitalsSchemaDef1)
        )
      )
      .map { json =>
        println(Json.prettyPrint(json))
      }
}
