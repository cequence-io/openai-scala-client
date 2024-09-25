package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.examples.fixtures.TestFixtures
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
        settings = DefaultSettings.createJsonChatCompletion(capitalsSchemaDef1)
      )
      .map { response =>
        val json = Json.parse(messageContent(response))
        println(Json.prettyPrint(json))
      }
}
