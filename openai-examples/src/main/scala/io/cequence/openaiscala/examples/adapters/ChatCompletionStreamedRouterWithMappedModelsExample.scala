package io.cequence.openaiscala.examples.adapters

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.MappedModel
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

/**
 * Requirements:
 *   - `openai-scala-client-stream` as a dependency
 *   - `OCTOAI_TOKEN` environment variable to be set
 *   - Ollama service running locally
 *
 * Here we demonstrate how to map models e.g. by prefixing them with the service name.
 */
object ChatCompletionStreamedRouterWithMappedModelsExample
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra] {

  // Note that creation of services here is a bit wasteful, since we are using only the streaming part

  // OctoML
  private val octoMLService = OpenAIChatCompletionServiceFactory.withStreaming(
    coreUrl = "https://text.octoai.run/v1/",
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env("OCTOAI_TOKEN")}"))
    )
  )

  // Ollama
  private val ollamaService = OpenAIChatCompletionServiceFactory.withStreaming(
    coreUrl = "http://localhost:11434/v1/"
  )

  // OpenAI
  private val openAIService = OpenAIServiceFactory.withStreaming()

  override val service: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionStreamedServiceRouter.applyMapped(
      // OpenAI service is default so no need to specify its models
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
      defaultService = openAIService
    )

  val messages: Seq[BaseMessage] = Seq(
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
    } yield ()

  private def runChatCompletionAux(model: String) = {
    println(s"Running chat completion with the model '$model'\n")

    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(model)
      )
      .runWith(
        Sink.foreach { completion =>
          val content = completion.choices.headOption.flatMap(_.delta.content)
          print(content.getOrElse(""))
        }
      )
      .map(_ => println("\n--------"))
  }
}
