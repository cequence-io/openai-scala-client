package io.cequence.openaiscala.examples.nonopenai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.perplexity.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings
import io.cequence.openaiscala.perplexity.service.{SonarService, SonarServiceFactory}

import scala.concurrent.Future

/**
 * Requires `SONAR_API_KEY` environment variable to be set.
 */
object SonarCreateChatCompletionStreamed extends ExampleBase[SonarService] {

  override val service: SonarService = SonarServiceFactory()

  private val messages = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.sonar

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = SonarCreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(512)
        )
      )
      .runWith(
        Sink.foreach { completion =>
          val content = completion.choices.headOption.flatMap(_.delta.content)
          print(content.getOrElse(""))
          if (completion.choices.headOption.exists(_.finish_reason.isDefined)) {
            println("\n\nCitations:\n" + completion.citations.mkString("\n"))
          }
        }
      )
}
