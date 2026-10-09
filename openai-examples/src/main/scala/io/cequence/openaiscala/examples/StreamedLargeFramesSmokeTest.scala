package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.{ModelId, NonOpenAIModelId}
import io.cequence.openaiscala.domain.responsesapi.ResponseStreamEvent._
import io.cequence.openaiscala.domain.responsesapi.tools.Tool
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ResponseStreamEvent
}
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.domain.{ChatRole, Content, Modality, Part}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{OpenAIServiceFactory, StreamingConsts}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

/**
 * Streams whose single events are larger than 1 MiB - the cap 1.4.0 hard-coded for every
 * stream (now `StreamingConsts.maxFrameLength`, 32 MiB unless configured):
 *
 *   - OpenAI's Responses API with the image generation tool: a partial image and the finished
 *     one arrive in base64, each in one event (and again in `response.completed`)
 *   - Gemini's image model: the picture arrives in one event
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `GOOGLE_API_KEY`; a run costs about $0.10.
 */
object StreamedLargeFramesSmokeTest {

  private val MiB = 1024 * 1024

  private val prompt =
    "A photorealistic, highly detailed picture of a crowded flower market at sunrise, " +
      "hundreds of different flowers, people, textures everywhere."

  private def mib(bytes: Int) = f"${bytes.toDouble / MiB}%.2f MiB"

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val system: ActorSystem = ActorSystem("streamed-large-frames")
    implicit val materializer: Materializer = Materializer(system)

    val openAI = OpenAIServiceFactory.withStreaming()
    val gemini = GeminiServiceFactory()

    println(s"Stream frame cap: ${mib(StreamingConsts.maxFrameLength)}")

    var failures = Seq.empty[String]

    def check(
      label: String,
      largest: Int,
      completed: Boolean
    ): Unit = {
      println(s"[$label] largest event ${mib(largest)}, completed: $completed")
      if (!completed) failures :+= s"$label: the stream did not complete"
      if (largest <= MiB) failures :+= s"$label: no event over 1 MiB (inconclusive)"
    }

    try {
      // 1. OpenAI Responses API - image generation with a partial image
      val events = Await.result(
        openAI
          .createModelResponseStreamed(
            Inputs.Text(prompt),
            CreateModelResponseSettings(
              model = ModelId.gpt_5_4_mini,
              tools = Seq(
                Tool.imageGeneration(
                  quality = Some("medium"),
                  size = Some("1024x1024"),
                  outputFormat = Some("png"),
                  partialImages = Some(1)
                )
              )
            )
          )
          .runWith(Sink.seq),
        5.minutes
      )

      def size(event: ResponseStreamEvent): Int = event match {
        case e: ImageGenerationPartialImage => e.partialImageB64.length
        case e: OutputItemDone              => e.raw.toString.length
        case e: ResponseCompleted           => e.raw.toString.length
        case _                              => 0
      }

      events.foreach { event =>
        if (size(event) > MiB)
          println(s"  ${event.getClass.getSimpleName}: ${mib(size(event))}")
      }
      check(
        "OpenAI Responses image generation",
        events.map(size).max,
        events.exists(_.isInstanceOf[ResponseCompleted])
      )

      // 2. Gemini image model
      val responses = Await.result(
        gemini
          .generateContentStreamed(
            Seq(Content.textPart(prompt, ChatRole.User)),
            GenerateContentSettings(
              NonOpenAIModelId.gemini_2_5_flash_image,
              generationConfig = Some(
                GenerationConfig(responseModalities = Some(Seq(Modality.TEXT, Modality.IMAGE)))
              )
            )
          )
          .runWith(Sink.seq),
        5.minutes
      )

      val images = responses.flatMap(_.candidates.flatMap(_.content.parts)).collect {
        case Part.InlineData(_, data) => data.length
      }
      check(
        "Gemini image model",
        (0 +: images).max,
        responses.exists(_.candidates.exists(_.finishReason.isDefined))
      )
    } catch {
      case NonFatal(e) =>
        failures :+= s"error: $e"
        e.printStackTrace()
    } finally {
      openAI.close()
      gemini.close()
      system.terminate()
    }

    println(if (failures.isEmpty) "ALL PASSED" else s"FAILED: ${failures.mkString("; ")}")
    System.exit(if (failures.isEmpty) 0 else 1)
  }
}
