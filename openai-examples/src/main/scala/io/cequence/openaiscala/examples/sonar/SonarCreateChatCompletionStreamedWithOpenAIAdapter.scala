package io.cequence.openaiscala.examples.sonar

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionStreamedServiceExtra

import scala.concurrent.Future
import io.cequence.openaiscala.examples.ChatCompletionProvider
import io.cequence.openaiscala.perplexity.service.SonarServiceConsts

// requires `openai-scala-client-stream` as a dependency and `SONAR_API_KEY` environment variable to be set
object SonarCreateChatCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra]
    with SonarServiceConsts {

  override val service: OpenAIChatCompletionStreamedServiceExtra =
    ChatCompletionProvider.sonar

  private val messages = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.sonar

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.01),
          max_tokens = Some(512),
          extra_params = Map(
            includeCitationsInTextResponseParam -> true
          )
        )
      )
      .runWith(
        Sink.foreach { completion =>
          print(completion.contentHead.getOrElse(""))
        }
      )
}
