package io.cequence.openaiscala.examples

import scala.concurrent.Future
object CreateAudioTranscription extends Example {

  private val audioFile = getClass.getResource("/wolfgang.mp3").getFile

  override protected def run: Future[Unit] =
    service
      .createAudioTranscription(
        new java.io.File(audioFile)
      )
      .map(response => println(response.text))
}
