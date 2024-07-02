package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  TextCompletionResponse
}
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  CreateCompletionSettings
}

trait OpenAIStreamedServiceExtra
    extends OpenAIChatCompletionStreamedServiceExtra
    with OpenAIServiceConsts {

  /**
   * Creates a completion for the provided prompt and parameters with streamed results.
   *
   * @param prompt
   *   The prompt(s) to generate completions for, encoded as a string, array of strings, array
   *   of tokens, or array of token arrays. Note that <|endoftext|> is the document separator
   *   that the model sees during training, so if a prompt is not specified the model will
   *   generate as if from the beginning of a new document.
   * @param settings
   * @return
   *   text completion response as a stream (source)
   *
   * @see
   *   <a href="https://beta.openai.com/docs/api-reference/completions/create">OpenAI Doc</a>
   */
  @Deprecated
  def createCompletionStreamed(
    prompt: String,
    settings: CreateCompletionSettings = DefaultSettings.CreateCompletion
  ): Source[TextCompletionResponse, NotUsed]

  /**
   * Creates a completion for the chat message(s) with streamed results. Note that this is
   * already defined in [[OpenAIChatCompletionStreamedServiceExtra]] but repeated here for
   * clarity.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[ChatCompletionChunkResponse, NotUsed]
}
