package io.cequence.openaiscala.examples.adapter

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._

import scala.concurrent.Future

/**
 * Requirements:
 *   - `openai-scala-client-stream` as a dependency
 *   - `OCTOAI_TOKEN` environment variable to be set
 *   - Ollama service running locally
 */
object ChatCompletionStreamedRouterExample
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra] {

  // Note that creation of services here is a bit wasteful, since we are using only the streaming part

  // OctoML
  private val octoMLService = OpenAIChatCompletionServiceFactory.withStreaming(
    coreUrl = "https://text.octoai.run/v1/",
    authHeaders = Seq(("Authorization", s"Bearer ${sys.env("OCTOAI_TOKEN")}"))
  )

  // Ollama
  private val ollamaService = OpenAIChatCompletionServiceFactory.withStreaming(
    coreUrl = "http://localhost:11434/v1/"
  )

  // OpenAI
  private val openAIService = OpenAIServiceFactory.withStreaming()

  override val service: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionStreamedServiceRouter(
      serviceModels = Map(
        octoMLService -> Seq(NonOpenAIModelId.mixtral_8x7b_instruct),
        ollamaService -> Seq(NonOpenAIModelId.llama2),
        // it's default so no need to specify all the models
        openAIService -> Seq(ModelId.gpt_3_5_turbo)
      ),
      defaultService = openAIService
    )

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      _ <- runChatCompletionAux(NonOpenAIModelId.mixtral_8x7b_instruct)

      _ <- runChatCompletionAux(NonOpenAIModelId.llama2)

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
