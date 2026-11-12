package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.concurrent.Futures
import io.cequence.wsclient.stream.{AsyncStream, Termination}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationLong
import scala.util.control.NonFatal

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override val service: OpenAIChatCompletionStreamedService =
    ChatCompletionProvider.anthropic()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val maxAttempts = 3

  override protected def run: Future[_] = {
    def createStream(): AsyncStream[String] =
      service
        .createChatCompletionStreamed(
          messages = messages,
          settings = CreateChatCompletionSettings(
            model = NonOpenAIModelId.claude_sonnet_5
          )
        )
        .map(
          _.choices.headOption.flatMap(_.delta.content).getOrElse("")
        )

    // restarts a failed stream from its beginning, after a growing pause, at most 3 times
    def withRetry(attempt: Int): AsyncStream[String] =
      createStream().recoverWith { case NonFatal(e) =>
        if (attempt < maxAttempts) {
          logger.warn(s"Attempt $attempt failed (${e.getMessage}) - restarting the stream.")
          AsyncStream.futureStream(
            Futures.delay((500L << (attempt - 1)).millis).map(_ => withRetry(attempt + 1))
          )
        } else
          AsyncStream.failed(new OpenAIScalaClientException("Max attempts reached", e))
      }

    withRetry(1).onTerminate {
      case Termination.Failed(ex) => logger.error("Response failed with an exception.", ex)
      case _                      => logger.debug("Response ended.")
    }.runForeach(print)
  }
}
