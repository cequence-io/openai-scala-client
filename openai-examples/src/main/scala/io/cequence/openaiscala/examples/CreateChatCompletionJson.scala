package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.examples.fixtures.TestFixtures

import scala.concurrent.Future

object CreateChatCompletionJson extends Example with TestFixtures {

  val messages = Seq(
    SystemMessage(capitalsPrompt),
    UserMessage("List only african countries")
  )

  override protected def run: Future[_] =
    service
      .createJsonChatCompletion(
        messages = messages,
        jsonSchema = capitalsSchema
      )
      .map { content =>
        printMessageContent(content)
      }
}
