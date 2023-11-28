package io.cequence.openaiscala.examples

object CreateAudioTranscription extends Example {

  override protected def run =
    service
      .createAudioTranscription(
        new java.io.File("speech-test.mp3")
      )
      .map(response => println(response.text))
}
