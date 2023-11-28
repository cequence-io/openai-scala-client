package io.cequence.openaiscala.examples

import akka.stream.scaladsl.FileIO
import java.nio.file.Paths

object CreateAudioSpeech extends Example {

  override protected def run =
    for {
      source <- service.createAudioSpeech(
        "Today is a wonderful day to build something people love!"
      )

      result <- source.runWith(FileIO.toPath(Paths.get("speech-test.mp3")))
    } yield {
      println(result.status)
    }
}