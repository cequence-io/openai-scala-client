package io.cequence.openaiscala.examples

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionServiceStreamedFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

object CreateChatCompletionStreamedOllama extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service = OpenAIChatCompletionServiceStreamedFactory.customInstance(
    coreUrl = "http://localhost:11434/v1/"
  )

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = "llama2",
          temperature = Some(0.1),
          max_tokens = Some(512),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .runWith(
        Sink.foreach { completion =>
          val content = completion.choices.headOption.flatMap(_.delta.content)
          println(content.getOrElse(""))
        }
      )
}
