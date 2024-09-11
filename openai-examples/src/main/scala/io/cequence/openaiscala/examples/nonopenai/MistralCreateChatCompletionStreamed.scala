package io.cequence.openaiscala.examples.nonopenai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIChatCompletionStreamedServiceFactory
}
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency and `MISTRAL_API_KEY` environment variable to be set
object MistralCreateChatCompletionStreamed
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra] {

  override val service: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionStreamedServiceFactory(
      coreUrl = "https://api.mistral.ai/v1/",
      WsRequestContext(authHeaders =
        Seq(("Authorization", s"Bearer ${sys.env("MISTRAL_API_KEY")}"))
      )
    )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.mistral_large_latest

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(512)
        )
      )
      .runWith(
        Sink.foreach { completion =>
          val content = completion.choices.headOption.flatMap(_.delta.content)
          print(content.getOrElse(""))
        }
      )
}
