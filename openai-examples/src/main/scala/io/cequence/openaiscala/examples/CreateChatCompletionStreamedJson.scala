package io.cequence.openaiscala.examples

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.examples.fixtures.TestFixtures
import io.cequence.openaiscala.service.{OpenAIServiceConsts, OpenAIServiceFactory}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency
object CreateChatCompletionStreamedJson
    extends ExampleBase[OpenAIStreamedService]
    with TestFixtures
    with OpenAIServiceConsts {

  override val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()

  private val messages = Seq(
    SystemMessage(capitalsPrompt),
    UserMessage("List all asian countries and their capitals.")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = DefaultSettings.createJsonChatCompletion(capitalsSchema)
      )
      .runWith(
        Sink.foreach { completion =>
          val content = completion.choices.headOption.flatMap(_.delta.content)
          print(content.getOrElse(""))
        }
      )
}
