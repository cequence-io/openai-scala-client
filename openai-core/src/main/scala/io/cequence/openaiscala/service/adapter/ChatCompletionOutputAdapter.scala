package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage, ChatCompletionTool}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapper

import scala.concurrent.{ExecutionContext, Future}

private class ChatCompletionOutputAdapter[S <: OpenAIChatCompletionService](
  adaptMessage: AssistantMessage => AssistantMessage
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
  ): Future[ChatCompletionResponse] =
    underlying.createChatCompletion(messages, settings).map { response =>
      response.copy(
        choices =
          response.choices.map(choice => choice.copy(message = adaptMessage(choice.message)))
      )
    }

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    underlying.createChatToolCompletion(messages, tools, responseToolChoice, settings).map {
      response =>
        response.copy(
          choices = response.choices.map { choice =>
            val message = choice.message
            // Only adapt when content is present so tool-call-only replies (content = None)
            // stay distinguishable from empty-text replies (content = Some("")).
            message.content match {
              case Some(content) =>
                val adapted = adaptMessage(AssistantMessage(content, message.name))
                choice.copy(message =
                  message.copy(content = Some(adapted.content), name = adapted.name)
                )
              case None =>
                choice
            }
          }
        )
    }

  override def close(): Unit =
    underlying.close()

}
