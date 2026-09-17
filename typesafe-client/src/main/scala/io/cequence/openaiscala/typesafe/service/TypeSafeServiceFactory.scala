package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.typesafe.service.impl.{
  OpenAITypeSafeChatCompletionService,
  TypeSafeServiceImpl
}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

/**
 * Creates [[TypeSafeService]] instances. Defaults come from the same environment variables the
 * official TypeSafe SDKs read: `TYPESAFE_API_KEY` (required), `TYPESAFE_BASE_URL` (optional,
 * `https://api.typesafe.ai`) and `TYPESAFE_DEFAULT_MODEL` (optional, `jev-latest`).
 *
 * {{{
 * implicit val ec = ExecutionContext.global
 *
 * val typeSafe = TypeSafeServiceFactory()   // TYPESAFE_API_KEY from the env
 *
 * typeSafe.systemOne(
 *   state = "I was charged twice. Please fix this ASAP.",
 *   questions = Map(
 *     "department" -> ChoiceQuestion("Which team?", "billing" -> "Payments", "technical" -> "Bugs"),
 *     "is_urgent" -> NoulQuestion("Does this convey urgency?")
 *   )
 * ).map { response =>
 *   response.choice("department").choice   // "billing"
 *   response.noul("is_urgent").noul        // 0.97
 * }
 * }}}
 */
object TypeSafeServiceFactory extends EnvHelper {

  import TypeSafeServiceConsts._

  /**
   * A service on its own PRIVATE engine (HTTP client + actor system), closed with the service.
   *
   * @param timeouts
   *   client-level timeouts (milliseconds); the official SDKs default to 10 s per attempt,
   *   which System One's ~100 ms answers rarely approach
   */
  def apply(
    apiKey: String = getEnvValue(apiKeyEnvKey),
    baseUrl: String = baseUrlFromEnv,
    defaultModel: String = defaultModelFromEnv,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(apiKey, baseUrl, defaultModel, timeouts)

  /**
   * A service on a CALLER-SUPPLIED, SITE-STATELESS engine - e.g. one shared with other
   * providers via `WSClientEngineRegistry()` / `StreamedEngineRegistry.outputStreamed()` - so
   * several services share one connection pool and actor system. Closing such a service does
   * NOT close the shared engine; close the engine once, when done with all services using it.
   */
  def withEngine(
    engine: WSClientEngine,
    apiKey: String = getEnvValue(apiKeyEnvKey),
    baseUrl: String = baseUrlFromEnv,
    defaultModel: String = defaultModelFromEnv
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(apiKey, baseUrl, defaultModel, externalEngine = Some(engine))

  /**
   * System One behind the OpenAI chat-completion interface - structured output ONLY: every
   * request must set `response_format_type = json_schema` with a closed-vocabulary
   * `jsonSchema` (booleans, string enums, numeric enums / small ranges, arrays of string
   * enums, objects of those); the schema becomes the questions, the messages the `state`, and
   * the assistant message's content is a JSON document of that schema. Anything else fails
   * fast with an explanation. Made for `createChatCompletionWithJSON[T]` (pass the model in
   * `jsonSchemaModels`, or rely on the `jev-*` entries of `models-supporting-json-schema`).
   */
  def asOpenAI(
    apiKey: String = getEnvValue(apiKeyEnvKey),
    baseUrl: String = baseUrlFromEnv,
    defaultModel: String = defaultModelFromEnv,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    new OpenAITypeSafeChatCompletionService(apply(apiKey, baseUrl, defaultModel, timeouts))

  /**
   * The OpenAI adapter over an EXISTING service (e.g. one on a shared engine, or retrying).
   */
  def asOpenAI(
    service: TypeSafeService
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    new OpenAITypeSafeChatCompletionService(service)

  private def baseUrlFromEnv: String = envOrElse(baseUrlEnvKey, defaultBaseUrl)

  private def defaultModelFromEnv: String =
    envOrElse(defaultModelEnvKey, TypeSafeServiceConsts.defaultModel)

  private def envOrElse(
    key: String,
    default: String
  ): String =
    Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty).getOrElse(default)
}
