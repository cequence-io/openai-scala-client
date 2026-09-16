package io.cequence.openaiscala.examples.fireworksai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * Streamed reasoning output from a DeepSeek model on Fireworks.
 *
 * DeepSeek-R1-era builds inlined the chain of thought as a `<think>` block in the content, so
 * getting at the answer meant stripping everything up to the closing tag - what the deprecated
 * `MessageConversions.filterOutToThinkEndFlow` adapter did. Current builds stream it in
 * `delta.reasoning_content` instead, which the chunk exposes as `reasoningText`, so the two
 * are already separate and no adapter is involved.
 *
 * Requires `openai-scala-client-stream` as a dependency and the `FIREWORKS_API_KEY` env var.
 */
object FireworksAICreateChatCompletionStreamedWithDeepseek
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = ChatCompletionProvider.fireworks

  private val fireworksModelPrefix = "accounts/fireworks/models/"

  private val messages = Seq(
    SystemMessage("You are a helpful assistant. Be short."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.deepseek_v4p1_flash

  override protected def run: Future[_] = {
    // label each side once, rather than per chunk
    var inReasoning = false
    var startedAnswer = false

    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + modelId,
          temperature = Some(0.01),
          max_tokens = Some(2048),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .runWith(
        Sink.foreach { completion =>
          val delta = completion.choices.headOption.map(_.delta)

          delta.flatMap(_.reasoningText).filter(_.nonEmpty).foreach { reasoning =>
            if (!inReasoning) {
              print("--- reasoning ---\n")
              inReasoning = true
            }
            print(reasoning)
          }

          delta.flatMap(_.content).filter(_.nonEmpty).foreach { content =>
            if (!startedAnswer) {
              print(s"${if (inReasoning) "\n\n" else ""}--- answer ---\n")
              startedAnswer = true
            }
            print(content)
          }
        }
      )
      .map(_ => println())
  }
}
