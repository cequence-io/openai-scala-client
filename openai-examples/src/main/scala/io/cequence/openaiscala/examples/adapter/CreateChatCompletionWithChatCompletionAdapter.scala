package io.cequence.openaiscala.examples.adapter

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.Future

object CreateChatCompletionWithChatCompletionAdapter extends ExampleBase[OpenAIService] {

  // OctoML
  private val octoMLService = OpenAIChatCompletionServiceFactory(
    coreUrl = "https://text.octoai.run/v1/",
    authHeaders = Seq(("Authorization", s"Bearer ${sys.env("OCTOAI_TOKEN")}"))
  )

  // Ollama
  private val ollamaService = OpenAIChatCompletionServiceFactory(
    coreUrl = "http://localhost:11434/v1/"
  )

  // OpenAI
  private val openAIService = OpenAIServiceFactory()

  override val service: OpenAIService = OpenAIServiceAdapters.forFullService.chatCompletion(
    // OpenAI service is default so no need to specify its models here
    serviceModels = Map(
      octoMLService -> Seq("mixtral-8x7b-instruct"),
      ollamaService -> Seq("llama2")
    ),
    openAIService
  )

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      // runs on OctoML
      _ <- runChatCompletionAux("mixtral-8x7b-instruct")

      // runs on Ollama
      _ <- runChatCompletionAux("llama2")

      // runs on OpenAI
      _ <- runChatCompletionAux(ModelId.gpt_3_5_turbo)

      // runs on OpenAI (non-chat-completion function)
      _ <- service.listModels.map(_.foreach(println))
    } yield ()

  private def runChatCompletionAux(model: String) = {
    println(s"Running chat completion with the model '$model'\n")

    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = model,
          temperature = Some(0.1),
          max_tokens = Some(1024),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .map(printMessageContent)
  }
}
