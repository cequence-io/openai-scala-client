package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.examples.fixtures.TestFixtures
import io.cequence.openaiscala.service.OpenAIServiceConsts

import scala.concurrent.Future

object CreateChatCompletionJson extends Example with TestFixtures with OpenAIServiceConsts {

  val messages = Seq(
    SystemMessage(capitalsPrompt),
    UserMessage("List only african countries")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = DefaultSettings.createJsonChatCompletion(capitalsSchema)
      )
      .map { content =>
        printMessageContent(content)
      }
}
