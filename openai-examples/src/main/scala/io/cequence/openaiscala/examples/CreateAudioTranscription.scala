package io.cequence.openaiscala.examples

import java.io.File
import scala.concurrent.Future
object CreateAudioTranscription extends Example {

  private val audioFile: String = Option(
    getClass.getClassLoader.getResource("question-last-164421.mp3")
  ).map(_.getFile).getOrElse(throw new RuntimeException("Audio file not found"))

  override protected def run: Future[Unit] = {
    service
      .createAudioTranscription(new File(audioFile))
      .map(response => println(response.text))
//    Future.successful(())
  }
}
