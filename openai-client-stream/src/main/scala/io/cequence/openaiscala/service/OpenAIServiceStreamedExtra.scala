package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.response.{FineTuneEvent, TextCompletionResponse}
import io.cequence.openaiscala.domain.settings.CreateCompletionSettings

trait OpenAIServiceStreamedExtra extends OpenAIServiceConsts {

  /**
   * Creates a completion for the provided prompt and parameters with streamed results.
   *
   * @param prompt The prompt(s) to generate completions for, encoded as a string, array of strings, array of tokens, or array of token arrays.
                   Note that <|endoftext|> is the document separator that the model sees during training,
                   so if a prompt is not specified the model will generate as if from the beginning of a new document.
   * @param settings
   * @return text completion response as a stream (source)
   *
   * @see <a href="https://beta.openai.com/docs/api-reference/completions/create">OpenAI Doc</a>
   */
  def createCompletionStreamed(
    prompt: String,
    settings: CreateCompletionSettings = DefaultSettings.CreateCompletion
  ): Source[TextCompletionResponse, NotUsed]

  /**
   * Get fine-grained status updates for a fine-tune job with streamed results.
   *
   * @param fineTuneId The ID of the fine-tune job to get events for.
   * @return fine tune events or None if not found as a stream (source)
   *
   * @see <a href="https://beta.openai.com/docs/api-reference/fine-tunes/events">OpenAI Doc</a>
   */
  def listFineTuneEventsStreamed(
    fineTuneId: String
  ): Source[FineTuneEvent, NotUsed]
}
