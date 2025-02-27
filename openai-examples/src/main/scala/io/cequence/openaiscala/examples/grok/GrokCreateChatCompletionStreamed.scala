package io.cequence.openaiscala.examples.grok

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionStreamedServiceExtra

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency and `GROK_API_KEY` environment variable to be set
object GrokCreateChatCompletionStreamed
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra] {

  override val service: OpenAIChatCompletionStreamedServiceExtra = ChatCompletionProvider.grok

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.grok_beta

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.01),
          max_tokens = Some(512)
        )
      )
      .runWith(
        Sink.foreach { completion =>
          print(completion.contentHead.getOrElse(""))
        }
      )
}
