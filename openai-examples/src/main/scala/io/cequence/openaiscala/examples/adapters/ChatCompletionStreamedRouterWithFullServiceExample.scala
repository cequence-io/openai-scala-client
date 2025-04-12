package io.cequence.openaiscala.examples.adapters

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service._
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

/**
 * Requirements:
 *   - `openai-scala-client-stream` as a dependency
 *   - `OCTOAI_TOKEN` environment variable to be set
 *   - Ollama service running locally
 *   - `ANTHROPIC_API_KEY` environment variable to be set
 *
 * Note that this is essentially the same example as [[ChatCompletionStreamedRouterExample]],
 * but here we demonstrate how "routed" streaming can be added to the full OpenAI service.
 */
object ChatCompletionStreamedRouterWithFullServiceExample
    extends ExampleBase[OpenAIService with OpenAIChatCompletionStreamedServiceExtra] {

  // OctoML
  private val octoMLService = OpenAIChatCompletionStreamedServiceFactory(
    coreUrl = "https://text.octoai.run/v1/",
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env("OCTOAI_TOKEN")}"))
    )
  )

  // Ollama
  private val ollamaService = OpenAIChatCompletionStreamedServiceFactory(
    coreUrl = "http://localhost:11434/v1/"
  )

  // Anthropic
  private val anthropicService = AnthropicServiceFactory.asOpenAI()

  // OpenAI
  private val openAIService = OpenAIStreamedServiceFactory()

  private val routedStreamedService: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionStreamedServiceRouter(
      // OpenAI service is default so no need to specify its models
      serviceModels = Map(
        octoMLService -> Seq(NonOpenAIModelId.mixtral_8x7b_instruct),
        ollamaService -> Seq(NonOpenAIModelId.llama2),
        anthropicService -> Seq(
          NonOpenAIModelId.claude_3_5_haiku_20241022
        )
      ),
      defaultService = openAIService
    )

  // now we create a new "full" OpenAI service and add the routed streaming to it
  override val service: OpenAIService with OpenAIChatCompletionStreamedServiceExtra =
    OpenAIServiceFactory().withStreaming(routedStreamedService)

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      // runs on OctoML
      _ <- runChatCompletionAux(NonOpenAIModelId.mixtral_8x7b_instruct)

      // runs on Ollama
      _ <- runChatCompletionAux(NonOpenAIModelId.llama2)

      // runs on Anthropic
      _ <- runChatCompletionAux(NonOpenAIModelId.claude_3_5_haiku_20241022)

      // runs on OpenAI
      _ <- runChatCompletionAux(ModelId.gpt_4o)

      // runs on OpenAI (non-chat-completion function)
      _ <- service.listModels.map(_.foreach(println))
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
