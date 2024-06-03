package io.cequence.openaiscala.examples

import scala.concurrent.Future
object CreateAudioTranscription extends Example {

  override protected def run: Future[Unit] =
    service
      .createAudioTranscription(
        new java.io.File("speech-test.mp3")
      )
      .map(response => println(response.text))
}
