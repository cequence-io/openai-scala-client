package io.cequence.openaiscala.service

// run me
object OpenAIExampleApp extends BaseOpenAIClientApp {

  openAIService.listModels
    .map(
      _.sortBy(_.created).foreach(println)
    )
    .closeAndExit()
}
