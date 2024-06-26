package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  CreateTranscriptionSettings,
  TranscriptResponseFormatType
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

/**
 * Requires `GROQ_API_KEY` environment variable to be set.
 */
object GroqCreateAudioTranscription extends ExampleBase[OpenAIService] {

  override val service: OpenAIService = OpenAIServiceFactory.customInstance(
    coreUrl = "https://api.groq.com/openai/v1/",
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env("GROQ_API_KEY")}"))
    )
  )

  private val audioFile = getClass.getResource("/wolfgang.mp3").getFile

  override protected def run: Future[_] =
    service
      .createAudioTranscription(
        new java.io.File(audioFile),
        settings = CreateTranscriptionSettings(
          model = NonOpenAIModelId.whisper_large_v3,
          response_format = Some(TranscriptResponseFormatType.verbose_json)
        )
      )
      .map(response => println(response.text))
}
