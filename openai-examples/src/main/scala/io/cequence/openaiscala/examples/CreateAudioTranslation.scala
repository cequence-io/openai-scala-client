package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings.{
  CreateTranslationSettings,
  TranscriptResponseFormatType
}
import scala.concurrent.Future

// translates to English
object CreateAudioTranslation extends Example {

  private val inputFileName = "openai-examples/src/main/resources/wolfgang.mp3"

  override protected def run: Future[Unit] =
    service
      .createAudioTranslation(
        file = new java.io.File(inputFileName),
        prompt = Some("Translate to English"),
        settings = CreateTranslationSettings(
          model = ModelId.whisper_1,
          temperature = Some(1),
          response_format = Some(TranscriptResponseFormatType.text)
        )
      )
      .map(response => println(response.text))
}
