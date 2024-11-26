package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage, SystemMessage, UserMessage}
import io.cequence.openaiscala.domain.response.{ChatCompletionChoiceInfo, ChatCompletionResponse, TextCompletionResponse}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, CreateCompletionSettings}
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAICompletionService}
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapper

import scala.concurrent.{ExecutionContext, Future}

private class ChatToCompletionAdapter[
  S <: OpenAICompletionService with OpenAIChatCompletionService
](
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
    underlying
      .createCompletion(
        prompt = messages.map {
          case m: SystemMessage    => m.content
          case m: UserMessage      => s"Q: ${m.content}"
          case m: AssistantMessage => s"A: ${m.content}"
          case m => throw new OpenAIScalaClientException("Unsupported message type: " + m)
        }.mkString("\n"),
        settings = toCompletionSettings(settings)
      )
      .map(toChatCompletionResponse)

  private def toCompletionSettings(
    settings: CreateChatCompletionSettings
  ) = CreateCompletionSettings(
    model = settings.model,
    suffix = None,
    max_tokens = settings.max_tokens,
    temperature = settings.temperature,
    top_p = settings.top_p,
    n = settings.n,
    logprobs = settings.top_logprobs,
    echo = None,
    stop = settings.stop,
    presence_penalty = settings.presence_penalty,
    frequency_penalty = settings.frequency_penalty,
    best_of = None,
    logit_bias = settings.logit_bias,
    user = settings.user,
    seed = settings.seed
  )

  private def toChatCompletionResponse(
    response: TextCompletionResponse
  ) = ChatCompletionResponse(
    id = response.id,
    created = response.created,
    model = response.model,
    system_fingerprint = response.system_fingerprint,
    choices = response.choices.map { choice =>
      ChatCompletionChoiceInfo(
        message = AssistantMessage(choice.text),
        index = choice.index,
        finish_reason = choice.finish_reason,
        logprobs = None // TODO: convert log probs
      )
    },
    usage = response.usage
  )

  override def close(): Unit =
    underlying.close()

}
