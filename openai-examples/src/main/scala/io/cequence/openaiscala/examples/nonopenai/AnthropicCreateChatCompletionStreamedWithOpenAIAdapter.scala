package io.cequence.openaiscala.examples.nonopenai

import akka.NotUsed
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.{DurationDouble, DurationInt}

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override val service: OpenAIChatCompletionStreamedService =
    ChatCompletionProvider.anthropic

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  @volatile var attemptCounter = 0
  private val maxAttempts = 3

  override protected def run: Future[_] = {
    def createSource(): Source[String, NotUsed] =
      service
        .createChatCompletionStreamed(
          messages = messages,
          settings = CreateChatCompletionSettings(
            model = NonOpenAIModelId.claude_3_5_sonnet_20240620
          )
        )
        .map(
          _.choices.headOption.flatMap(_.delta.content).getOrElse("")
        )

    val sourceWithRetry: Source[String, _] = RestartSource.onFailuresWithBackoff(
      minBackoff = 0.5.seconds,
      maxBackoff = 20.seconds,
      randomFactor = 0.2,
      maxRestarts = 3
    ) { () =>
      attemptCounter += 1
      if (attemptCounter <= maxAttempts) {
        createSource()
      } else {
        Source.failed(new OpenAIScalaClientException("Max attempts reached"))
      }
    }

    sourceWithRetry
      .watchTermination()(
        (
          _,
          done
        ) => {
          done.onComplete {
            case scala.util.Success(_) =>
              logger.debug("Response completed successfully.")

            case scala.util.Failure(ex) =>
              logger.error("Response failed with an exception.", ex)
          }
        }
      )
      .runWith(
        Sink.foreach(print)
      )
  }
}
