package io.cequence.openaiscala.examples.minimax

import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/**
 * MiniMax's chat API, MiniMax-M3.1-Flash by default. Requires MINIMAX_API_KEY. Optional
 * arguments: a model id (e.g. MiniMax-M3), then "china" for the China endpoint
 * (api.minimaxi.com).
 */
object CreateMiniMaxChatCompletion {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val model = args.headOption.getOrElse(NonOpenAIModelId.minimax_m3_1_flash)
    val provider =
      if (args.lift(1).contains("china"))
        ChatProviderSettings.minimaxChina
      else
        ChatProviderSettings.minimax

    val service = OpenAIChatCompletionServiceFactory(provider)

    try {
      val response = Await.result(
        service.createChatCompletion(
          Seq(UserMessage("Hello!")),
          CreateChatCompletionSettings(model)
        ),
        2.minutes
      )
      println(response.contentHead)
    } finally service.close()
  }
}
