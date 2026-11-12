package io.cequence.openaiscala.examples

import java.nio.file.Paths
import scala.concurrent.Future

object CreateAudioSpeech extends Example {

  override protected def run: Future[Unit] =
    for {
      source <- service.createAudioSpeech(
        "Today is a wonderful day to build something people love!"
      )

      bytesWritten <- StreamToFile(source, Paths.get("speech-test.mp3"))
    } yield println(s"$bytesWritten bytes written")
}
