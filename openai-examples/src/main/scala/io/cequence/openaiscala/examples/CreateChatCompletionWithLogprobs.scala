package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

object CreateChatCompletionWithLogprobs extends Example {

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_3_5_turbo,
          temperature = Some(0),
          max_tokens = Some(100),
          logprobs = Some(true),
          top_logprobs = Some(3)
        )
      )
      .map { response =>
        printMessageContent(response)
        val logprobs = response.choices.head.logprobs.map(_.content).getOrElse(Nil)
        logprobs.foreach { logprob =>
          println(s"Logprob: ${logprob.token} -> ${logprob.logprob}, top: ${logprob.top_logprobs.map(_.token).mkString(", ")}")
        }
      }
}
