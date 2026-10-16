package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ModelId,
  NonOpenAIModelId,
  ProviderSettings,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.perplexity.service.SonarServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.service.{
  BedrockAuth,
  ChatProviderSettings,
  OpenAICoreServiceFactory,
  OpenAIServiceFactory
}
import io.cequence.wsclient.domain.WsRequestContext

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live connectivity sweep over the provider hosts the library calls through its HTTP engine
 * (ws-client's Play WS engine) - meant for engine upgrades: ws-client 1.1.2 changed the TLS
 * protocols the engine offers (TLS 1.3 + 1.2, the JDK's defaults, instead of AHC's TLS 1.2 /
 * 1.1 / 1). For every target whose environment variables are set: the model listing (where the
 * host has one), one short chat completion and one streamed completion, each through the
 * library's own factory for that host, on a private engine.
 *
 * A failure is reported with its kind: `TLS/CONNECT` (no HTTP exchange at all - a handshake,
 * certificate or connection failure) or `HTTP` (the host answered, e.g. a 402 for an account
 * without credits) - only the first kind is a connectivity problem, and only it sets the exit
 * code (1). Run it with `-Djavax.net.debug=ssl:handshake` to see the TLS version each host
 * negotiates ("Negotiated protocol version").
 *
 * Arguments: target names to run (default: every target with its variables set), and
 * `model.<target>=<id>` to override a model. Targets and their variables: `openai`
 * (`OPENAI_SCALA_CLIENT_API_KEY`), `anthropic` (`ANTHROPIC_API_KEY`), `anthropic-foundry`
 * (`ANTHROPIC_FOUNDRY_BASE_URL` + `ANTHROPIC_FOUNDRY_API_KEY`), `bedrock-runtime`
 * (`AWS_BEDROCK_ACCESS_KEY` + `AWS_BEDROCK_SECRET_KEY` + `AWS_BEDROCK_REGION`),
 * `bedrock-mantle` (`AWS_BEARER_TOKEN_BEDROCK` + `AWS_BEDROCK_REGION`), `gemini` and
 * `gemini-openai` (`GOOGLE_API_KEY`), `groq`, `cerebras`, `fireworks`, `mistral`, `together`,
 * `grok`, `deepseek`, `novita`, `sambanova` (`SAMBANOVA_API_KEY`), `cohere` (`COHERE_API_KEY`,
 * its compatibility API), `meta` (`MODEL_API_KEY`), `openrouter`, `vercel`
 * (`AI_GATEWAY_API_KEY`, the AI Gateway's chat API), `liquid` (Liquid's chat API) and
 * `perplexity-agent` (`PERPLEXITY_API_KEY`). The decision hosts are covered by
 * `typesafe.DecisionModelsLatencyBenchmark`. Any other OpenAI-compatible host: `url=<base
 * URL>` with an optional `key=<variable>` (its bearer key) and `model=<id>` - also a way to
 * reach a test server, e.g. `url=https://tls-v1-2.badssl.com:1012/` takes TLS 1.2 only (it
 * answers with HTTP errors, which is fine: the handshake is what counts).
 */
object ProviderConnectivitySmokeTest {

  private final case class Probe(
    service: OpenAIChatCompletionStreamedService,
    listing: Option[() => Future[Seq[String]]],
    close: () => Unit
  )

  private final case class Target(
    name: String,
    env: Seq[String],
    model: String,
    open: () => Probe
  )

  private val messages = Seq(
    SystemMessage("You are a terse assistant."),
    UserMessage("Reply with the single word OK.")
  )

  // an OpenAI-compatible host: the listing, the chat and the stream through one service
  private def openAICompatible(
    url: String,
    keyEnv: Option[String]
  )(
    implicit ec: ExecutionContext
  ): Probe = {
    val service = OpenAICoreServiceFactory.withStreaming(
      url,
      WsRequestContext(authHeaders =
        keyEnv.toSeq.map(name => "Authorization" -> s"Bearer ${sys.env(name)}")
      )
    )
    Probe(service, Some(() => service.listModels.map(_.map(_.id))), () => service.close())
  }

  private def targets(
    implicit ec: ExecutionContext
  ): Seq[Target] = {
    def single(service: OpenAIChatCompletionStreamedService) =
      Probe(service, None, () => service.close())

    def compatible(
      name: String,
      settings: ProviderSettings,
      model: String
    ) =
      Target(
        name,
        Seq(settings.apiKeyEnvVariable),
        model,
        () => openAICompatible(settings.coreUrl, Some(settings.apiKeyEnvVariable))
      )

    Seq(
      Target(
        "openai",
        Seq("OPENAI_SCALA_CLIENT_API_KEY"),
        ModelId.gpt_5_4_mini,
        () => {
          val service = OpenAIServiceFactory.withStreaming()
          Probe(
            service,
            Some(() => service.listModels.map(_.map(_.id))),
            () => service.close()
          )
        }
      ),
      Target(
        "anthropic",
        Seq("ANTHROPIC_API_KEY"),
        NonOpenAIModelId.claude_haiku_4_5,
        () => single(AnthropicServiceFactory.asOpenAI())
      ),
      Target(
        "anthropic-foundry",
        Seq("ANTHROPIC_FOUNDRY_BASE_URL", "ANTHROPIC_FOUNDRY_API_KEY"),
        NonOpenAIModelId.claude_sonnet_4_5,
        () =>
          single(
            AnthropicServiceFactory.asOpenAI(
              AnthropicServiceFactory.customInstance(
                // the base of `messages`, also when given the full endpoint
                sys
                  .env("ANTHROPIC_FOUNDRY_BASE_URL")
                  .stripSuffix("/")
                  .stripSuffix("/messages") + "/",
                WsRequestContext(authHeaders =
                  Seq(
                    "x-api-key" -> sys.env("ANTHROPIC_FOUNDRY_API_KEY"),
                    "anthropic-version" -> "2023-06-01"
                  )
                )
              )
            )
          )
      ),
      Target(
        "bedrock-runtime",
        Seq("AWS_BEDROCK_ACCESS_KEY", "AWS_BEDROCK_SECRET_KEY", "AWS_BEDROCK_REGION"),
        "global." + NonOpenAIModelId.bedrock_claude_haiku_5_5,
        () => single(AnthropicServiceFactory.bedrockAsOpenAI())
      ),
      Target(
        "bedrock-mantle",
        Seq("AWS_BEARER_TOKEN_BEDROCK", "AWS_BEDROCK_REGION"),
        ModelId.bedrock_openai_gpt_oss_20b,
        () => {
          val service = OpenAIServiceFactory.withStreaming.forBedrock(
            BedrockAuth.BearerToken(sys.env("AWS_BEARER_TOKEN_BEDROCK")),
            region = sys.env("AWS_BEDROCK_REGION")
          )
          Probe(
            service,
            Some(() => service.listModels.map(_.map(_.id))),
            () => service.close()
          )
        }
      ),
      Target(
        "gemini",
        Seq("GOOGLE_API_KEY"),
        NonOpenAIModelId.gemini_2_5_flash_lite,
        () => {
          val native = GeminiServiceFactory()
          val service = GeminiServiceFactory.asOpenAI()
          Probe(
            service,
            Some(() => native.listModels().map(_.models.map(_.name))),
            () => { service.close(); native.close() }
          )
        }
      ),
      compatible(
        "gemini-openai",
        ChatProviderSettings.gemini,
        NonOpenAIModelId.gemini_2_5_flash_lite
      ),
      compatible("groq", ChatProviderSettings.groq, "openai/gpt-oss-20b"),
      compatible("cerebras", ChatProviderSettings.cerebras, "qwen-3.8-27b"),
      compatible(
        "fireworks",
        ChatProviderSettings.fireworks,
        "accounts/fireworks/models/gpt-oss-120b"
      ),
      compatible("mistral", ChatProviderSettings.mistral, "ministral-3b-latest"),
      // gpt-oss-20b is a dedicated-endpoint model there (400 "Unable to access non-serverless model")
      compatible("together", ChatProviderSettings.togetherAI, "openai/gpt-oss-120b"),
      compatible("grok", ChatProviderSettings.grok, "grok-4.20-0309-non-reasoning"),
      compatible("deepseek", ChatProviderSettings.deepseek, "deepseek-flash"),
      compatible("novita", ChatProviderSettings.novita, "openai/gpt-oss-120b"),
      compatible(
        "sambanova",
        ProviderSettings("https://api.sambanova.ai/v1/", "SAMBANOVA_API_KEY"),
        "gpt-oss-120b"
      ),
      compatible(
        "cohere",
        ProviderSettings("https://api.cohere.ai/compatibility/v1/", "COHERE_API_KEY"),
        "command-r7b-12-2024"
      ),
      compatible("meta", ChatProviderSettings.meta, "muse-spark-1.3-contributor"),
      compatible("openrouter", ChatProviderSettings.openRouter, "liquid/lfm-2.5-2.6b:free"),
      compatible("vercel", ChatProviderSettings.vercel, "google/gemini-2.5-flash-lite"),
      compatible("liquid", ChatProviderSettings.liquid, "lfm2.5-24b-a2b"),
      Target(
        "perplexity-agent",
        Seq("PERPLEXITY_API_KEY"),
        "openai/gpt-5.4-mini",
        () => single(SonarServiceFactory.agentAsOpenAI())
      )
    )
  }

  // TLS / connection trouble anywhere in the cause chain - no HTTP exchange took place
  private def connectivityProblem(e: Throwable): Boolean =
    Iterator.iterate(e)(_.getCause).takeWhile(_ != null).take(10).exists { t =>
      val message = Option(t.getMessage).getOrElse("").toLowerCase
      t.isInstanceOf[javax.net.ssl.SSLException] ||
      t.isInstanceOf[java.net.ConnectException] ||
      Seq("handshake", "close_notify", "certificate", "ssl", "connection refused").exists(
        message.contains
      )
    }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher

    def arg(name: String) = args.find(_.startsWith(name + "=")).map(_.drop(name.length + 1))

    val overrides = args.collect {
      case arg if arg.startsWith("model.") && arg.contains("=") =>
        val (key, value) = arg.drop("model.".length).span(_ != '=')
        key -> value.drop(1)
    }.toMap
    val only = args.filterNot(_.contains("=")).toSet
    val custom = arg("url").map { url =>
      Target(
        "custom",
        arg("key").toSeq,
        arg("model").getOrElse("gpt-5.4-mini"),
        () => openAICompatible(url, arg("key"))
      )
    }

    val failures = new AtomicInteger(0)
    val connectivityFailures = new AtomicInteger(0)

    def step[T](
      target: String,
      name: String
    )(
      f: => Future[T]
    )(
      describe: T => String
    ): Unit = {
      val start = System.currentTimeMillis()
      val result = Try(Await.result(Try(f).fold(Future.failed, identity), 3.minutes))
      val ms = System.currentTimeMillis() - start
      result match {
        case Success(value) =>
          println(s"[PASS] $target / $name ($ms ms): ${describe(value)}")
        case Failure(e) =>
          failures.incrementAndGet()
          val kind =
            if (connectivityProblem(e)) {
              connectivityFailures.incrementAndGet()
              "TLS/CONNECT"
            } else "HTTP"
          val message = Option(e.getMessage).getOrElse("").replaceAll("\\s+", " ").take(200)
          println(
            s"[FAIL] $target / $name ($ms ms) [$kind]: ${e.getClass.getSimpleName}: $message"
          )
      }
    }

    val selected =
      custom.fold(targets.filter(t => only.isEmpty || only.contains(t.name)))(Seq(_))
    selected.foreach { target =>
      val missing = target.env.filterNot(name => sys.env.get(name).exists(_.trim.nonEmpty))
      if (missing.nonEmpty)
        println(s"[SKIP] ${target.name}: ${missing.mkString(", ")} not set")
      else {
        val model = overrides.getOrElse(target.name, target.model)
        val settings = CreateChatCompletionSettings(model, max_tokens = Some(256))

        Try(target.open()) match {
          case Failure(e) =>
            failures.incrementAndGet()
            println(
              s"[FAIL] ${target.name} / setup: ${e.getClass.getSimpleName}: ${e.getMessage}"
            )

          case Success(probe) =>
            probe.listing.foreach { listing =>
              step(target.name, "listing")(listing()) { ids =>
                s"${ids.size} models" + (if (ids.exists(_.endsWith(model)))
                                           s", $model among them"
                                         else s", $model NOT listed")
              }
            }
            step(target.name, s"chat $model")(
              probe.service.createChatCompletion(messages, settings)
            ) { response =>
              val text = response.choices.headOption.map(_.message.content).getOrElse("")
              s"'${text.replaceAll("\\s+", " ").take(40)}' (${response.usage.map(_.total_tokens).getOrElse("-")} tokens)"
            }
            step(target.name, "stream")(
              probe.service.createChatCompletionStreamed(messages, settings).runWith(Sink.seq)
            ) { chunks =>
              val text = chunks.flatMap(_.choices.flatMap(_.delta.content)).mkString
              s"${chunks.size} chunks, '${text.replaceAll("\\s+", " ").take(40)}'"
            }
            Try(probe.close())
        }
      }
    }

    println(
      s"Summary: ${selected.size} targets, ${failures.get} failed steps, " +
        s"${connectivityFailures.get} of them TLS/connection failures"
    )
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (connectivityFailures.get > 0) 1 else 0)
  }
}
