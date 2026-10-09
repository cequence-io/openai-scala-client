package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.domain.WsRequestContext
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * The OpenAI adapter over Anthropic's access modes - the same checks (a chat with reasoning, a
 * tool call on the typed stream, json_schema structured output) on each:
 *
 *   - `api` - the Claude API with an API key (`asOpenAI()`, `ANTHROPIC_API_KEY`), on Claude
 *     Haiku 5.5
 *   - `oauth` - the Claude API with an OAuth token (`asOpenAIWithAuthToken()`,
 *     `ANTHROPIC_AUTH_TOKEN` / `CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE` /
 *     `CLAUDE_CODE_OAUTH_TOKEN`), on Claude Haiku 4.5: a Claude subscription's token serves
 *     the other models to Claude Code only, and Anthropic answers them with a 429
 *     `rate_limit_error` "Error" otherwise (live 2026-10-07, also checked here) - use an API
 *     key for them
 *   - `foundry` - Microsoft Foundry, the same Messages API at
 *     `https://<resource>.services.ai.azure.com/anthropic/v1/` with an `x-api-key`
 *     (`customInstance`; `ANTHROPIC_FOUNDRY_BASE_URL`, `ANTHROPIC_FOUNDRY_API_KEY`), on the
 *     resource's deployment (`ANTHROPIC_FOUNDRY_MODEL`, default `claude-sonnet-4-5`)
 *   - `bedrock` - Amazon Bedrock with SigV4 (`bedrockAsOpenAI()`, `AWS_BEDROCK_ACCESS_KEY` /
 *     `AWS_BEDROCK_SECRET_KEY`) or a Bedrock API key (`bedrockAsOpenAIWithBearerToken()`,
 *     `AWS_BEARER_TOKEN_BEDROCK`), `AWS_BEDROCK_REGION` (default `eu-central-1`), on Claude
 *     Haiku 5.5's `global.` profile (json_schema via `output_config.format`, which Bedrock
 *     takes for it despite Anthropic's docs - live 2026-10-07)
 *
 * A mode without its credentials is skipped; pass mode names as args to run only those. Every
 * check prints PASS/FAIL and the run goes on; the exit code is 1 if any failed.
 */
object AnthropicAccessModesSmokeTest {

  private case class Capital(
    country: String,
    capital: String
  )

  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  private val capitalSchema = JsonSchemaDef(
    name = "capital",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq("country" -> JsonSchema.String(), "capital" -> JsonSchema.String()),
        required = Seq("country", "capital")
      )
    )
  )

  private val weatherTool = FunctionTool(
    name = "get_weather",
    description = Some("Get the current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String(description = Some("City name"))),
      required = Seq("city")
    )
  )

  private def env(name: String): Option[String] =
    sys.env.get(name).map(_.trim).filter(_.nonEmpty)

  /** A mode: its name, the service (None = no credentials) and the model it serves. */
  private case class Mode(
    name: String,
    service: () => Option[OpenAIChatCompletionStreamedService],
    model: String
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val region = env("AWS_BEDROCK_REGION").getOrElse("eu-central-1")
    val bedrockModel = "global." + NonOpenAIModelId.bedrock_claude_haiku_5_5

    val modes = Seq(
      Mode(
        "api",
        () => env("ANTHROPIC_API_KEY").map(_ => AnthropicServiceFactory.asOpenAI()),
        NonOpenAIModelId.claude_haiku_5_5
      ),
      Mode(
        "oauth",
        () =>
          Seq(
            "ANTHROPIC_AUTH_TOKEN",
            "CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE",
            "CLAUDE_CODE_OAUTH_TOKEN"
          ).flatMap(env).headOption.map(_ => AnthropicServiceFactory.asOpenAIWithAuthToken()),
        NonOpenAIModelId.claude_haiku_4_5
      ),
      Mode(
        "foundry",
        () =>
          for {
            baseUrl <- env("ANTHROPIC_FOUNDRY_BASE_URL")
            apiKey <- env("ANTHROPIC_FOUNDRY_API_KEY")
          } yield AnthropicServiceFactory.asOpenAI(
            AnthropicServiceFactory.customInstance(
              // the base of `messages`, also when given the full endpoint
              baseUrl.stripSuffix("/").stripSuffix("/messages") + "/",
              WsRequestContext(
                authHeaders = Seq("x-api-key" -> apiKey, "anthropic-version" -> "2023-06-01")
              )
            )
          ),
        env("ANTHROPIC_FOUNDRY_MODEL").getOrElse(NonOpenAIModelId.claude_sonnet_4_5)
      ),
      Mode(
        "bedrock (SigV4)",
        () =>
          for {
            _ <- env("AWS_BEDROCK_ACCESS_KEY")
            _ <- env("AWS_BEDROCK_SECRET_KEY")
          } yield AnthropicServiceFactory.bedrockAsOpenAI(region = region),
        bedrockModel
      ),
      Mode(
        "bedrock (API key)",
        () =>
          env("AWS_BEARER_TOKEN_BEDROCK").map(_ =>
            AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(region = region)
          ),
        bedrockModel
      )
    ).filter(mode => args.isEmpty || args.exists(arg => mode.name.startsWith(arg)))

    val failures = new AtomicInteger(0)

    def check(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      Try(f).fold(Future.failed, identity).transform {
        case Success(msg) =>
          println(s"  [PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
          Success(())
        case Failure(e) =>
          failures.incrementAndGet()
          println(
            s"  [FAIL] $name: ${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("").take(300)}"
          )
          Success(())
      }
    }

    def run(
      mode: Mode,
      service: OpenAIChatCompletionStreamedService
    ): Future[Unit] = {
      val settings = CreateChatCompletionSettings(model = mode.model, max_tokens = Some(4000))
      for {
        _ <- check("chat, reasoning_effort=low") {
          service
            .createChatCompletion(
              Seq(UserMessage("What is 17*23? Answer with the number only.")),
              settings.copy(reasoning_effort = Some(ReasoningEffort.low))
            )
            .map { r =>
              if (!r.contentHead.contains("391")) sys.error(s"wrong answer: ${r.contentHead}")
              s"model ${r.model}, '${r.contentHead}'"
            }
        }
        _ <- check("typed stream, a tool call") {
          service
            .createChatToolCompletionStreamed(
              Seq(UserMessage("What's the weather in Paris right now?")),
              Seq(weatherTool),
              None,
              settings
            )
            .assembled
            .map { a =>
              if (a.toolCalls.isEmpty) sys.error(s"no tool call: ${a.text}")
              s"calls=${a.toolCalls.map(c => s"${c.toolName}(${c.arguments})")}, finish=${a.finishReason}"
            }
        }
        _ <- check("createChatCompletionWithJSON (json_schema)") {
          service
            .createChatCompletionWithJSON[Capital](
              Seq(UserMessage("Capital of Norway?")),
              settings.copy(
                response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
                jsonSchema = Some(capitalSchema)
              )
            )
            .map(c => s"parsed=$c")
        }
      } yield ()
    }

    val all = modes.foldLeft(Future.unit) {
      (
        previous,
        mode
      ) =>
        previous.flatMap { _ =>
          Try(mode.service()) match {
            case Success(None) =>
              println(s"[${mode.name}] skipped - no credentials")
              Future.unit
            case Failure(e) =>
              failures.incrementAndGet()
              println(s"[${mode.name}] [FAIL] could not be created: $e")
              Future.unit
            case Success(Some(service)) =>
              println(s"[${mode.name}] ${mode.model}")
              val oauthOther =
                if (mode.name != "oauth") Future.unit
                else
                  // the other models: refused outside Claude Code - how the client reports it
                  service
                    .createChatCompletion(
                      Seq(UserMessage("Say hi")),
                      CreateChatCompletionSettings(NonOpenAIModelId.claude_haiku_5_5)
                    )
                    .transform { result =>
                      println(
                        s"  [INFO] ${NonOpenAIModelId.claude_haiku_5_5} with the OAuth token: " +
                          result.fold(
                            e => s"${e.getClass.getSimpleName}: ${e.getMessage}",
                            r => s"answered: ${r.contentHead}"
                          )
                      )
                      Success(())
                    }
              run(mode, service).flatMap(_ => oauthOther).andThen { case _ => service.close() }
          }
        }
    }

    Try(Await.result(all, 15.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: $e")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
