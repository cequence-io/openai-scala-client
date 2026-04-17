package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{
  BaseMessage,
  ChatCompletionInterceptData,
  ChatCompletionTool
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapper

import scala.concurrent.{ExecutionContext, Future}

private class ChatCompletionInterceptAdapter[S <: OpenAIChatCompletionService](
  intercept: ChatCompletionInterceptData => Future[Unit],
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

  // but for the chat completion we adapt the messages and settings
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val timeRequestReceived = new java.util.Date()

    for {
      response <- underlying.createChatCompletion(
        messages,
        adjustSettingsForCall(settings)
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

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] = {
    val timeRequestReceived = new java.util.Date()

    for {
      response <- underlying.createChatToolCompletion(
        messages,
        tools,
        responseToolChoice,
        adjustSettingsForCall(settings)
      )

      _ <- {
        val timeResponseReceived = new java.util.Date()

        // TODO: toChatCompletionResponse strips tool_calls from the intercept payload.
        intercept(
          ChatCompletionInterceptData(
            messages,
            settings,
            response.toChatCompletionResponse,
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
