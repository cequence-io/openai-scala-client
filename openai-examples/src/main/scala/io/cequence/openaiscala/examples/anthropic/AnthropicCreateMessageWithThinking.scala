package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMessageSettings,
  ThinkingSettings
}
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageWithThinking extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant who knows elfs personally."),
    UserMessage("What is the weather like in Norway with your local insights?")
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.claude_3_7_sonnet_20250219,
          max_tokens = 10000,
          thinking = Some(ThinkingSettings(budget_tokens = 2000))
        )
      )
      .map { response =>
        println("Response:\n" + response.text)

        println("Thinking:\n" + response.thinkingText)

        val usage = response.usage

        println(s"""Usage:
          |Input tokens  : ${usage.input_tokens}
          |(cache create): ${usage.cache_creation_input_tokens.getOrElse(0)}
          |(cache read)  : ${usage.cache_read_input_tokens.getOrElse(0)}
          |Output tokens : ${usage.output_tokens}
          |""".stripMargin)
      }
}
