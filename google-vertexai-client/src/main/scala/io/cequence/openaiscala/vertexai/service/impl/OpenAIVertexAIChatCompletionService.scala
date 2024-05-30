package io.cequence.openaiscala.vertexai.service.impl

import akka.NotUsed
import akka.stream.scaladsl.{Source, StreamConverters}
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.GenerateContentResponse
import com.google.cloud.vertexai.generativeai.GenerativeModel
import io.cequence.openaiscala.domain.{BaseMessage, ChatRole}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChunkMessageSpec,
  UsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}

import java.util.concurrent.CompletableFuture
import scala.collection.convert.ImplicitConversions.`seq AsJavaList`
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

// TODO: convert Google exceptions (e.g. com.google.api.gax.rpc.ResourceExhaustedException) to OpenAI exceptions
private[service] class OpenAIVertexAIChatCompletionService(
  underlying: VertexAI
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val model = createModel(messages, settings)

    val javaFuture = model.generateContentAsync(toNonSystemVertexAI(messages))
    val scalaFuture: Future[GenerateContentResponse] =
      toScala(CompletableFuture.supplyAsync(() => javaFuture.get))

    scalaFuture.map { response =>
      toOpenAI(response, settings.model)
    }
  }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] = {
    val model = createModel(messages, settings)

    val javaStream = model.generateContentStream(toNonSystemVertexAI(messages))
    val scalaStream = StreamConverters.fromJavaStream(() => javaStream.stream())

    scalaStream.map { response =>
      val openAIResponse = toOpenAI(response, settings.model)

      ChatCompletionChunkResponse(
        id = openAIResponse.id,
        created = openAIResponse.created,
        model = openAIResponse.model,
        system_fingerprint = openAIResponse.system_fingerprint,
        choices = openAIResponse.choices.map(info =>
          ChatCompletionChoiceChunkInfo(
            delta = ChunkMessageSpec(
              Some(ChatRole.Assistant),
              Some(info.message.content)
            ),
            index = info.index,
            finish_reason = info.finish_reason
          )
        ),
        usage = openAIResponse.usage
      )
    }
  }

  private def createModel(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): GenerativeModel = {
    val config = toVertexAI(settings)

    val modelAux = new GenerativeModel(settings.model, underlying).withGenerationConfig(config)

    toSystemVertexAI(messages)
      .map(
        modelAux.withSystemInstruction
      )
      .getOrElse(
        modelAux
      )
  }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
