package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionErrorInterceptData}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapper

import scala.concurrent.{ExecutionContext, Future}

private class ChatCompletionErrorInterceptAdapter[S <: OpenAIChatCompletionService](
  intercept: ChatCompletionErrorInterceptData => Future[Unit],
  adjustSettingsForCall: CreateChatCompletionSettings => CreateChatCompletionSettings
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

  // but for the chat completion we intercept errors
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val timeRequestSent = new java.util.Date()

    underlying
      .createChatCompletion(
        messages,
        adjustSettingsForCall(settings)
      )
      .recoverWith { case e: Throwable =>
        val timeErrorReceived = new java.util.Date()

        intercept(
          ChatCompletionErrorInterceptData(
            messages,
            settings,
            e,
            timeRequestSent,
            timeErrorReceived
          )
        ).recover { case _ => () }.flatMap(_ => Future.failed(e))
      }
  }

  override def close(): Unit =
    underlying.close()
}
