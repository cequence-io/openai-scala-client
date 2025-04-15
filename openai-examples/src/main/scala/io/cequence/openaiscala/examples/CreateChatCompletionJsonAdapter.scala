package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIServiceConsts
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

object CreateChatCompletionJsonAdapter
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
          model = ModelId.gpt_4_1,
          max_tokens = Some(5000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(capitalsSchemaDef1)
        )
      )
      .map { json =>
        println(Json.prettyPrint(json))
      }
}
