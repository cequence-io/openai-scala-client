package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Source, Zip}
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.{ChatCompletionChunkResponse, ChunkMessageSpec}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

object OpenAIChatCompletionStreamedOutputConversionAdapter {
  def apply(
    service: OpenAIChatCompletionStreamedServiceExtra,
    messageConversion: Flow[Seq[ChunkMessageSpec], Seq[ChunkMessageSpec], NotUsed]
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedOutputConversionAdapterImpl(
      service,
      messageConversion
    )

  final private class OpenAIChatCompletionStreamedOutputConversionAdapterImpl(
    underlying: OpenAIChatCompletionStreamedServiceExtra,
    messageConversion: Flow[Seq[ChunkMessageSpec], Seq[ChunkMessageSpec], NotUsed]
  ) extends OpenAIChatCompletionStreamedServiceExtra {

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      underlying
        .createChatCompletionStreamed(
          messages,
          settings
        )
        .via(conversionStream(messageConversion))

    private def conversionStream(
      messageProcessingFlow: Flow[Seq[ChunkMessageSpec], Seq[ChunkMessageSpec], NotUsed]
    ): Flow[ChatCompletionChunkResponse, ChatCompletionChunkResponse, NotUsed] =
      Flow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        // Broadcast each ChatCompletionResponse into 2 identical copies
        val bcast = builder.add(Broadcast[ChatCompletionChunkResponse](2))

        // Zip them back together at the end: left side is the original response,
        // right side is the updated Seq[ChatChoice].
        val zip = builder.add(Zip[ChatCompletionChunkResponse, Seq[ChunkMessageSpec]]())

        // Subflow #1: pass the original response (for final zip)
        bcast.out(0) ~> zip.in0

        // Subflow #2: extract the choices, process them, feed into zip
        val extractDeltas = Flow[ChatCompletionChunkResponse].map(_.choices.map(_.delta))
        bcast.out(1) ~> extractDeltas ~> messageProcessingFlow ~> zip.in1

        // Once we zip, we get (originalResponse, updatedChoices)
        // Turn that into a single ChatCompletionResponse with new choices
        val mergeBack = Flow[(ChatCompletionChunkResponse, Seq[ChunkMessageSpec])].map {
          case (response, updatedChoices) =>
            response.copy(
              choices =
                response.choices.zip(updatedChoices).map { case (choice, updatedChoice) =>
                  choice.copy(delta = updatedChoice)
                }
            )
        }

        // Wire zip's output into mergeBack
        val mergeBackStage = builder.add(mergeBack)
        zip.out ~> mergeBackStage

        // The final shape: in => zip.out => merge => out
        FlowShape(bcast.in, mergeBackStage.out)
      })

    override def close(): Unit =
      underlying.close()
  }
}
