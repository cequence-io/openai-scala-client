package io.cequence.openaiscala.service

object OpenAIExampleApp extends BaseOpenAIClientApp {

  openAIService.listModels
    .map(
      _.foreach(println)
    )
    .onComplete { _ =>
      openAIService.close
      system.terminate
      System.exit(0)
    }
}
