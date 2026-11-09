package io.cequence.openaiscala.service

import io.cequence.wsclient.stream.{AsyncPipe, AsyncStream}
import io.cequence.openaiscala.domain.ChatCompletionTool
import io.cequence.openaiscala.domain.response.ChatChunk

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.{ChatCompletionChunkResponse, ChunkMessageSpec}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

/**
 * Converts the output of a streamed service: the OpenAI-shaped chunks' deltas through
 * `messageConversion`, the typed chunks through `chunkConversion`.
 *
 * `messageConversion` must be 1:1 - one output per input, in order (each converted delta list
 * is put back into the chunk it came from); a pipe that emits more fails the stream, one that
 * drops elements misaligns them. A native pipe (e.g. an Akka `Flow` via `.toAsyncPipe`) runs
 * natively on its own backend.
 */
object OpenAIChatCompletionStreamedOutputConversionAdapter {
  def apply(
    service: OpenAIChatCompletionStreamedServiceExtra,
    messageConversion: AsyncPipe[Seq[ChunkMessageSpec], Seq[ChunkMessageSpec]],
    chunkConversion: AsyncPipe[ChatChunk, ChatChunk] = AsyncPipe.identity[ChatChunk]
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedOutputConversionAdapterImpl(
      service,
      messageConversion,
      chunkConversion
    )

  final private class OpenAIChatCompletionStreamedOutputConversionAdapterImpl(
    underlying: OpenAIChatCompletionStreamedServiceExtra,
    messageConversion: AsyncPipe[Seq[ChunkMessageSpec], Seq[ChunkMessageSpec]],
    chunkConversion: AsyncPipe[ChatChunk, ChatChunk]
  ) extends OpenAIChatCompletionStreamedServiceExtra {

    override def createChatToolCompletionStreamed(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): AsyncStream[ChatChunk] =
      underlying
        .createChatToolCompletionStreamed(messages, tools, responseToolChoice, settings)
        .via(chunkConversion)

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): AsyncStream[ChatCompletionChunkResponse] =
      underlying
        .createChatCompletionStreamed(
          messages,
          settings
        )
        .via(conversionStream(messageConversion))

    // each chunk's deltas through the conversion, put back into the chunk they came from
    private def conversionStream(
      messageProcessingFlow: AsyncPipe[Seq[ChunkMessageSpec], Seq[ChunkMessageSpec]]
    ): AsyncPipe[ChatCompletionChunkResponse, ChatCompletionChunkResponse] =
      AsyncPipe
        .of[ChatCompletionChunkResponse]
        .zipVia(_.choices.map(_.delta), messageProcessingFlow) {
          (
            response,
            updatedChoices
          ) =>
            response.copy(
              choices =
                response.choices.zip(updatedChoices).map { case (choice, updatedChoice) =>
                  choice.copy(delta = updatedChoice)
                }
            )
        }

    override def close(): Unit =
      underlying.close()
  }
}
