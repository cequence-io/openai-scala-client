package io.cequence.openaiscala.examples.minimax

import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/** Requires MINIMAX_API_KEY. Optional arguments: model ID, then "china" for that region. */
object CreateMiniMaxChatCompletion {
  private[minimax] def settings(args: Array[String]): CreateChatCompletionSettings =
    CreateChatCompletionSettings(
      args.headOption.getOrElse(NonOpenAIModelId.minimax_m3_1_flash)
    )

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val provider =
      if (args.lift(1).contains("china")) ChatProviderSettings.minimaxChina
      else ChatProviderSettings.minimax
    val service = OpenAIChatCompletionServiceFactory(provider)
    try {
      val response = Await.result(
        service.createChatCompletion(Seq(UserMessage("Hello!")), settings(args)),
        2.minutes
      )
      println(response)
    } finally service.close()
  }
}
