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

object CreateChatCompletionJsonAndRepair
    extends Example
    with TestFixtures
    with OpenAIServiceConsts {

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a genius!"),
    UserMessage(
      """Output a malformed JSON as it is:{"name': "John", age: 30, "city": ""New York""}"""
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.o3_mini,
          max_tokens = Some(5000),
          response_format_type = Some(ChatCompletionResponseFormatType.text)
        )
      )
      .map { json =>
        println(Json.prettyPrint(json))
      }
}
