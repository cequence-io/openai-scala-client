package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._

import scala.concurrent.Future

object AnthropicRetryAdapterExample extends ExampleBase[OpenAIChatCompletionService] {

  private val failingModel = NonOpenAIModelId.claude_3_opus_20240229
  private val workingModel = NonOpenAIModelId.claude_3_haiku_20240307

  override protected val service: OpenAIChatCompletionService = AnthropicTestHelper.timoutingService

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      // this invokes the failing service, which triggers the retry mechanism
      _ <- runChatCompletionAux(failingModel).recover { case e: OpenAIScalaClientException =>
        println(s"Too many retries, giving up on '${e.getMessage}'")
      }

      // should complete without retry
      _ <- runChatCompletionAux(workingModel)
    } yield ()

  private def runChatCompletionAux(model: String) = {
    println(s"Running chat completion with the model '$model'\n")

    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = model,
          max_tokens = Some(4096)
        )
      )
      .map { response =>
        printMessageContent(response)
        println("--------")
      }
  }
}
