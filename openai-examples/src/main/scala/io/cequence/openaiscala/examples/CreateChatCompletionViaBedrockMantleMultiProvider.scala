package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.responsesapi.{CreateModelResponseSettings, Inputs}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.util.Try

/**
 * Calls a mix of third-party models on Amazon Bedrock's `bedrock-mantle` endpoint - one
 * service, one region, several providers (OpenAI OSS, Qwen, Z.ai; all Chat Completions from
 * the plain `v1` base path, `https://bedrock-mantle.<region>.api.aws/v1/...`), plus
 * `google.gemma-4-31b`, which is Responses-API-only (rejects `/v1/chat/completions` with
 * "model isn't supported on this route"; the AWS "API compatibility by models" page lists the
 * Gemma 3 family as Chat-Completions-only, but Gemma 4 behaves differently - verified live
 * July 2026). A single service instance handles them all - only the `model` field (and for
 * gemma the API used) changes per request.
 *
 * '''Advertised is not served''' (live findings, July 2026): a model appearing in a region's
 * `/v1/models` does NOT guarantee it responds there - unserved models simply HANG (no error,
 * SSE keep-alives only) rather than fail fast, while a model absent from the region's list
 * fails fast with a clean 404. E.g. `nvidia.nemotron-nano-3-30b` and
 * `mistral.ministral-3-8b-instruct` answered in under a second in `us-east-2` while hanging
 * for 30+ minutes in `eu-central-1`. Hence each call below has its own recover (a hanging
 * model reports a timeout and the sweep continues) and the service uses explicit timeouts.
 *
 * '''JSON-schema mode''' (second sweep below): all five chat models ACCEPT `response_format =
 * json_schema` on mantle (live-verified `eu-north-1`, July 2026), but enforcement differs -
 * `openai.gpt-oss-120b`, the qwen models, and `zai.glm-5` return clean schema-conforming JSON,
 * while `openai.gpt-oss-20b` may prefix prose before the JSON object (accepted but not
 * strictly enforced). The sweep reports each model as conforming JSON / non-JSON output /
 * request rejected.
 *
 * Set the `AWS_BEARER_TOKEN_BEDROCK` env var (Bedrock API key) before running.
 */
object CreateChatCompletionViaBedrockMantleMultiProvider extends ExampleBase[OpenAIService] {

  private val region = "eu-north-1"

  private val timeoutMs = 3 * 60 * 1000 // tolerate cold starts, give up on unserved models

  override protected val service: OpenAIService =
    OpenAIServiceFactory.forBedrockMantle(
      region = region,
      timeouts = Some(
        Timeouts(
          requestTimeout = Some(timeoutMs),
          readTimeout = Some(timeoutMs)
        )
      )
    )

  private val chatCompletionModels = Seq(
    // Context window128K tokens
    // Max output16K tokens
    // Input price$0.15 / 1M tokens
    // Output price$0.6 / 1M tokens
    NonOpenAIModelId.bedrock_openai_gpt_oss_120b,
    // Context window128K tokens
    // Max output16K tokens
    // Input price$0.07 / 1M tokens
    // Output price$0.3 / 1M tokens
    NonOpenAIModelId.bedrock_openai_gpt_oss_20b,
    // Context window256K tokens
    // Max output16K tokens
    // Input price$0.6 / 1M tokens
    // Output price$1.44 / 1M tokens
    NonOpenAIModelId.bedrock_qwen_qwen3_coder_next,
    // Context window256K tokens
    // Max output8K tokens
    // Input price$0.22 / 1M tokens
    // Output price$0.88 / 1M tokens
    NonOpenAIModelId.bedrock_qwen_qwen3_235b_a22b_2507,
    // Context window200K tokens
    // Max output128K tokens
    // Input price$1.20 / 1M tokens
    // Output price$3.84 / 1M tokens
    NonOpenAIModelId.bedrock_zai_glm_5
  )

  private val question = "What is the capital of Norway? One word."

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage(question)
  )

  private val capitalSchemaDef = JsonSchemaDef(
    name = "capital_response",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "country" -> JsonSchema.String(),
          "capital" -> JsonSchema.String()
        ),
        required = Seq("country", "capital")
      )
    )
  )

  override protected def run: Future[_] = {
    println(s"region: $region\n")

    val chatCompletions = chatCompletionModels.foldLeft(Future.unit: Future[Any]) {
      (
        acc,
        model
      ) => acc.flatMap(_ => reportingErrors(model)(chatCompletion(model)))
    }

    // gemma-4 is Responses-API-only on mantle
    val gemmaModel = NonOpenAIModelId.bedrock_google_gemma_4_31b
    val plainSweep =
      chatCompletions.flatMap(_ =>
        reportingErrors(gemmaModel)(gemmaViaResponsesAPI(gemmaModel))
      )

    plainSweep.flatMap { _ =>
      println("\njson_schema mode:\n")
      chatCompletionModels.foldLeft(Future.unit: Future[Any]) {
        (
          acc,
          model
        ) => acc.flatMap(_ => reportingErrors(model)(jsonSchemaCompletion(model)))
      }
    }
  }

  private def chatCompletion(model: String): Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model = model)
      )
      .map { response =>
        val usage = response.usage
          .map(u => s" (${u.prompt_tokens} in / ${u.completion_tokens.getOrElse(0)} out)")
          .getOrElse("")
        println(s"$model: ${response.contentHead.trim}$usage")
      }

  private def gemmaViaResponsesAPI(model: String): Future[_] =
    service
      .createModelResponse(
        Inputs.Text(question),
        settings = CreateModelResponseSettings(model = model)
      )
      .map { response =>
        val usage = response.usage
          .map(u => s" (${u.inputTokens} in / ${u.outputTokens} out)")
          .getOrElse("")
        println(s"$model (Responses API): ${response.outputText.getOrElse("N/A").trim}$usage")
      }

  private def jsonSchemaCompletion(model: String): Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = model,
          max_tokens = Some(2000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(capitalSchemaDef)
        )
      )
      .map { response =>
        val content = response.contentHead.trim
        Try(Json.parse(content)).toOption match {
          case Some(json) => println(s"$model: OK - ${Json.stringify(json)}")
          case None       => println(s"$model: NON-JSON output - ${content.take(120)}")
        }
      }

  private def reportingErrors(model: String)(call: => Future[_]): Future[_] =
    call.recover { case e =>
      println(s"$model: FAILED - ${e.getMessage.linesIterator.next().take(160)}")
    }
}
