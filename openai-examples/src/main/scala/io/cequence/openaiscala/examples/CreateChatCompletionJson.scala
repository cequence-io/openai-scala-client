package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.service.OpenAIServiceConsts
import play.api.libs.json.Json

import scala.concurrent.Future

object CreateChatCompletionJson extends Example with TestFixtures with OpenAIServiceConsts {

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage(capitalsPrompt),
    UserMessage("List only african countries")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_4_5_preview,
          max_tokens = Some(1000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(capitalsSchemaDef1)
        )
      )
      .map { response =>
        val json = Json.parse(response.contentHead)
        println(Json.prettyPrint(json))
      }
}
