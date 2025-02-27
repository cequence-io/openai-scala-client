package io.cequence.openaiscala.examples

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency
object CreateChatCompletionStreamed extends ExampleBase[OpenAIStreamedService] {

  override val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(ModelId.gpt_4o_mini)
      )
      .runWith(
        Sink.foreach { completion =>
          print(completion.contentHead.getOrElse(""))
        }
      )
}
