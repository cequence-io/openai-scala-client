package io.cequence.openaiscala.examples.adapter

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.{MappedModel, OpenAIServiceAdapters}
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

/**
 * Requirements:
 *   - `OCTOAI_TOKEN` environment variable to be set
 *   - Ollama service running locally
 *
 * Here we demonstrate how to map models e.g. by prefixing them with the service name.
 */
object ChatCompletionRouterAdapterWithMappedModelsExample extends ExampleBase[OpenAIService] {

  // OctoML
  private val octoMLService = OpenAIChatCompletionServiceFactory(
    coreUrl = "https://text.octoai.run/v1/",
    WsRequestContext(
      authHeaders = Seq(("Authorization", s"Bearer ${sys.env("OCTOAI_TOKEN")}"))
    )
  )

  // Ollama
  private val ollamaService = OpenAIChatCompletionServiceFactory(
    coreUrl = "http://localhost:11434/v1/"
  )

  // OpenAI
  private val openAIService = OpenAIServiceFactory()

  override val service: OpenAIService =
    OpenAIServiceAdapters.forFullService.chatCompletionRouterMapped(
      // OpenAI service is default so no need to specify its models here
      serviceModels = Map(
        octoMLService -> Seq(
          MappedModel(
            "octoML-" + NonOpenAIModelId.llama_2_13b_chat,
            NonOpenAIModelId.llama_2_13b_chat
          )
        ),
        ollamaService -> Seq(
          MappedModel(
            "ollama-" + NonOpenAIModelId.llama2,
            NonOpenAIModelId.llama2
          )
        )
      ),
      openAIService
    )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      // runs on OctoML
      _ <- runChatCompletionAux("octoML-" + NonOpenAIModelId.llama_2_13b_chat)

      // runs on Ollama
      _ <- runChatCompletionAux("ollama-" + NonOpenAIModelId.llama2)

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
        settings = CreateChatCompletionSettings(model)
      )
      .map { response =>
        printMessageContent(response)
        println("--------")
      }
  }
}
