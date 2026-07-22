package io.cequence.openaiscala.examples.adapters

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.StreamedEngineRegistry
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}

import scala.concurrent.Future

/**
 * Routes streamed chat completions across seven providers backed by ONE stateless engine: a
 * single connection pool and actor system serve all seven services below, each service just
 * holding its own site binding (base URL + auth headers) and threading it into every call on
 * the shared engine. Closing a routed service does NOT close the shared engine - it is closed
 * exactly once, at the end. (A previous version of this example created an independent engine
 * \- pool + actor system - per provider and facet, ~8 in total, which its own comment called
 * "a bit wasteful".)
 *
 * Requirements:
 *   - Include `openai-scala-client-stream` as a dependency.
 *   - Set the `OCTOAI_TOKEN` environment variable.
 *   - Set the `ANTHROPIC_API_KEY` environment variable.
 *   - Ensure the Ollama service is running locally.
 *   - Set the `FIREWORKS_API_KEY` environment variable.
 *   - Set the `AZURE_AI_COHERE_R_PLUS_ENDPOINT`, `AZURE_AI_COHERE_R_PLUS_REGION`, and
 *     `AZURE_AI_COHERE_R_PLUS_ACCESS_KEY` environment variables.
 *   - Set the `GROQ_API_KEY` environment variable.
 *   - Set the `OPENAI_SCALA_CLIENT_API_KEY` environment variable.
 */
object ChatCompletionStreamedRouterExample
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra] {

  // ONE stateless engine (connection pool + actor system) shared by every provider below
  private val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
    StreamedEngineRegistry.outputStreamed()

  private def bearerAuth(token: String): WsRequestContext =
    WsRequestContext(authHeaders = Seq(("Authorization", s"Bearer $token")))

  // OctoML
  private val octoMLService = OpenAIChatCompletionServiceFactory.withStreaming.withEngine(
    engine,
    "https://text.octoai.run/v1/",
    bearerAuth(sys.env("OCTOAI_TOKEN"))
  )

  // Ollama
  private val ollamaService = OpenAIChatCompletionServiceFactory.withStreaming.withEngine(
    engine,
    "http://localhost:11434/v1/"
  )

  // Fireworks AI
  private val fireworksModelPrefix = "accounts/fireworks/models/"
  private val fireworksService = OpenAIChatCompletionServiceFactory.withStreaming.withEngine(
    engine,
    "https://api.fireworks.ai/inference/v1/",
    bearerAuth(sys.env("FIREWORKS_API_KEY"))
  )

  // Anthropic - its own auth scheme and error taxonomy ride on the site binding that
  // AnthropicServiceFactory.withEngine builds internally
  private val anthropicService = AnthropicServiceFactory.asOpenAI(
    AnthropicServiceFactory.withEngine(engine)
  )

  // Azure AI - Cohere R+
  private val azureAICohereRPlusService =
    OpenAIChatCompletionServiceFactory.withStreaming.withEngine(
      engine,
      s"https://${sys.env("AZURE_AI_COHERE_R_PLUS_ENDPOINT")}.${sys.env("AZURE_AI_COHERE_R_PLUS_REGION")}.inference.ai.azure.com/v1/",
      bearerAuth(sys.env("AZURE_AI_COHERE_R_PLUS_ACCESS_KEY"))
    )

  // Groq
  private val groqService = OpenAIChatCompletionServiceFactory.withStreaming.withEngine(
    engine,
    "https://api.groq.com/openai/v1/",
    bearerAuth(sys.env("GROQ_API_KEY"))
  )

  // OpenAI
  private val openAIService = OpenAIChatCompletionServiceFactory.withStreaming.withEngine(
    engine,
    "https://api.openai.com/v1/",
    bearerAuth(sys.env("OPENAI_SCALA_CLIENT_API_KEY"))
  )

  override val service: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionStreamedServiceRouter(
      // OpenAI service is default so no need to specify its models
      serviceModels = Map(
        octoMLService -> Seq(NonOpenAIModelId.mixtral_8x7b_instruct),
        ollamaService -> Seq(NonOpenAIModelId.llama2),
        fireworksService -> Seq(fireworksModelPrefix + NonOpenAIModelId.llama_v2_13b_chat),
        anthropicService -> Seq(
          NonOpenAIModelId.claude_2_1,
          NonOpenAIModelId.claude_3_haiku_20240307
        ),
        azureAICohereRPlusService -> Seq(NonOpenAIModelId.cohere_command_r_plus),
        groqService -> Seq(NonOpenAIModelId.llama3_70b_8192)
      ),
      defaultService = openAIService
    )

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    (for {
      // runs on OctoML
      _ <- runChatCompletionAux(NonOpenAIModelId.mixtral_8x7b_instruct)

      // runs on Ollama
      _ <- runChatCompletionAux(NonOpenAIModelId.llama2)

      // runs on Fireworks AI
      _ <- runChatCompletionAux(fireworksModelPrefix + NonOpenAIModelId.llama_v2_13b_chat)

      // runs on Anthropic
      _ <- runChatCompletionAux(NonOpenAIModelId.claude_3_haiku_20240307)

      // runs on Azure AI
      _ <- runChatCompletionAux(NonOpenAIModelId.cohere_command_r_plus)

      // runs on Groq
      _ <- runChatCompletionAux(NonOpenAIModelId.llama3_70b_8192)

      // runs on OpenAI
      _ <- runChatCompletionAux(ModelId.gpt_3_5_turbo)
    } yield ()).andThen {
      // the routed services on the shared engine don't close it when they close - it's closed
      // exactly once, here
      case _ => engine.close()
    }

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
