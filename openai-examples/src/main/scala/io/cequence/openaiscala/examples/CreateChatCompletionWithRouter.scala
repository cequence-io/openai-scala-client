package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIChatCompletionServiceRouter
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

object CreateChatCompletionWithRouter extends ExampleBase[OpenAIChatCompletionService] {

  // OctoML
  private val octoMLService = OpenAIChatCompletionServiceFactory(
    coreUrl = "https://text.octoai.run/v1/",
    requestContext = WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env("OCTOAI_TOKEN")}"))
    )
  )

  // Ollama
  private val ollamaService = OpenAIChatCompletionServiceFactory(
    coreUrl = "http://localhost:11434/v1/"
  )

  // OpenAI
  private val openAIService = OpenAIServiceFactory()

  override val service = OpenAIChatCompletionServiceRouter(
    serviceModels = Map(
      octoMLService -> Seq("mixtral-8x7b-instruct"),
      ollamaService -> Seq("llama2"),
      // it's default so no need to specify all the models
      openAIService -> Seq(ModelId.gpt_3_5_turbo)
    ),
    defaultService = openAIService
  )

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      _ <- runChatCompletionAux("mixtral-8x7b-instruct")
      _ <- runChatCompletionAux("llama2")
      _ <- runChatCompletionAux(ModelId.gpt_3_5_turbo)
    } yield ()

  private def runChatCompletionAux(model: String) = {
    println(s"Running chat completion with the model '$model'\n")

    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = model,
          temperature = Some(0.1),
          max_tokens = Some(512),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .map(printMessageContent)
  }
}
