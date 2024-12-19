package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionInterceptData}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapper

import scala.concurrent.{ExecutionContext, Future}

private class ChatCompletionInterceptAdapter[S <: OpenAIChatCompletionService](
  intercept: ChatCompletionInterceptData => Future[Unit]
)(
  underlying: S
)(
  implicit ec: ExecutionContext
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService {

  // we just delegate all the calls to the underlying service
  override def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  // but for the chat completion we adapt the messages and settings
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val timeRequestReceived = new java.util.Date()

    for {
      response <- underlying.createChatCompletion(
        messages,
        settings
      )

      _ <- {
        val timeResponseReceived = new java.util.Date()

        intercept(
          ChatCompletionInterceptData(
            messages,
            settings,
            response,
            timeRequestReceived,
            timeResponseReceived
          )
        )
      }
    } yield response
  }

  override def close(): Unit =
    underlying.close()
}
