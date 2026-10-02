package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.typesafe.domain.DecisionProvider
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
 *
 * Other hosts of decision models speak the same protocol - pass one of the
 * [[DecisionProviderSettings]] (like `ChatProviderSettings` for chat providers), or a
 * [[io.cequence.openaiscala.typesafe.domain.DecisionProvider]] of your own:
 *
 * {{{
 * val openRouter = TypeSafeServiceFactory(DecisionProviderSettings.openRouter) // OPENROUTER_API_KEY
 * openRouter.systemOne(state, questions, TypeSafeModelId.openrouter_liquid_d1)
 *
 * val decider = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.perplexity)
 * }}}
 *
 * Their errors are the same [[TypeSafeScalaClientException]] hierarchy, classified by HTTP
 * status. [[liquid]] and [[perplexity]] (and their `WithEngine` / `AsOpenAI` forms) are
 * shorthands for the Liquid AI and Perplexity providers.
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

  /**
   * The OpenAI adapter over an EXISTING service, saying whether its host reads images in the
   * state - `imageInput = true` for a Perplexity service ([[perplexity]], e.g. wrapped in the
   * retry adapter): the image parts of user messages then go into the state instead of being
   * refused. TypeSafe's Jev and Liquid's d1 read an image part as text, so keep it `false` for
   * them.
   */
  def asOpenAI(
    service: TypeSafeService,
    imageInput: Boolean
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    new OpenAITypeSafeChatCompletionService(service, imageInput)

  /**
   * A service for a host of decision models - one of the [[DecisionProviderSettings]] or a
   * [[io.cequence.openaiscala.typesafe.domain.DecisionProvider]] of your own - the key from
   * its environment variable, on its own PRIVATE engine.
   */
  def apply(
    provider: DecisionProvider
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    forProvider(provider)

  /**
   * `apply(provider)` with an explicit key (instead of the provider's environment variable)
   * and client-level timeouts (milliseconds).
   */
  def forProvider(
    provider: DecisionProvider,
    apiKey: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(
      apiKey.getOrElse(provider.apiKeyFromEnv),
      provider.baseUrl,
      provider.defaultModel,
      timeouts,
      provider = provider
    )

  /** A service for a host of decision models on a CALLER-SUPPLIED, shared engine. */
  def withEngine(
    engine: WSClientEngine,
    provider: DecisionProvider
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    withEngine(engine, provider, provider.apiKeyFromEnv)

  /** `withEngine(engine, provider)` with an explicit key. */
  def withEngine(
    engine: WSClientEngine,
    provider: DecisionProvider,
    apiKey: String
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(
      apiKey,
      provider.baseUrl,
      provider.defaultModel,
      externalEngine = Some(engine),
      provider = provider
    )

  /**
   * A host of decision models behind the OpenAI chat-completion interface - `json_schema`
   * structured output only, like [[asOpenAI]]; image content goes into the state when the host
   * reads images (`provider.images`).
   */
  def asOpenAI(
    provider: DecisionProvider
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    asOpenAI(apply(provider), provider.images)

  /**
   * Liquid AI's decision model d1 on its System One API (`https://api.liquid.ai/decisions`,
   * `LIQUID_API_KEY`, `d1:free`), on its own PRIVATE engine.
   */
  def liquid(
    apiKey: String = getEnvValue(liquidApiKeyEnvKey),
    baseUrl: String = liquidBaseUrl,
    defaultModel: String = liquidDefaultModel,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(
      apiKey,
      baseUrl,
      defaultModel,
      timeouts,
      provider = DecisionProviderSettings.liquid
    )

  /** [[liquid]] on a CALLER-SUPPLIED, shared engine (see [[withEngine]]). */
  def liquidWithEngine(
    engine: WSClientEngine,
    apiKey: String = getEnvValue(liquidApiKeyEnvKey),
    baseUrl: String = liquidBaseUrl,
    defaultModel: String = liquidDefaultModel
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(
      apiKey,
      baseUrl,
      defaultModel,
      externalEngine = Some(engine),
      provider = DecisionProviderSettings.liquid
    )

  /**
   * Liquid AI's d1 behind the OpenAI chat-completion interface - `json_schema` structured
   * output only, exactly like [[asOpenAI]] (`d1:free` is in `models-supporting-json-schema`).
   */
  def liquidAsOpenAI(
    apiKey: String = getEnvValue(liquidApiKeyEnvKey),
    baseUrl: String = liquidBaseUrl,
    defaultModel: String = liquidDefaultModel,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    asOpenAI(liquid(apiKey, baseUrl, defaultModel, timeouts))

  /**
   * Perplexity's decision model `pplx-decider-v1-27b` on its Decisions API (`POST
   * https://api.perplexity.ai/v1/decisions`; the key from `PERPLEXITY_API_KEY`, else
   * `SONAR_API_KEY`), on its own PRIVATE engine. The same questions and answers as System One,
   * plus images: an OpenAI-style `image_url` part anywhere in the state, as a base64 PNG, JPEG
   * or WebP data URL of at most 2,048 tiles of 32 x 32 pixels (e.g. 1440 x 1440) - both
   * checked before sending, since a larger image times out (504) after about a minute. Limits
   * of its own: at most 128 questions and an input under 262,144 tokens. `listModels` returns
   * the one model without a request (Perplexity's `/v1/models` lists its Agent API models).
   */
  def perplexity(
    apiKey: String = DecisionProviderSettings.perplexity.apiKeyFromEnv,
    baseUrl: String = DecisionProviderSettings.perplexity.baseUrl,
    defaultModel: String = DecisionProviderSettings.perplexity.defaultModel,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(
      apiKey,
      baseUrl,
      defaultModel,
      timeouts,
      provider = DecisionProviderSettings.perplexity
    )

  /** [[perplexity]] on a CALLER-SUPPLIED, shared engine (see [[withEngine]]). */
  def perplexityWithEngine(
    engine: WSClientEngine,
    apiKey: String = DecisionProviderSettings.perplexity.apiKeyFromEnv,
    baseUrl: String = DecisionProviderSettings.perplexity.baseUrl,
    defaultModel: String = DecisionProviderSettings.perplexity.defaultModel
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(
      apiKey,
      baseUrl,
      defaultModel,
      externalEngine = Some(engine),
      provider = DecisionProviderSettings.perplexity
    )

  /**
   * Perplexity's decider behind the OpenAI chat-completion interface - `json_schema`
   * structured output only, like [[asOpenAI]], but the image parts of user messages
   * (`ImageURLContent` with a data URL) go into the state (`pplx-decider-v1-27b` is in
   * `models-supporting-json-schema`).
   */
  def perplexityAsOpenAI(
    apiKey: String = DecisionProviderSettings.perplexity.apiKeyFromEnv,
    baseUrl: String = DecisionProviderSettings.perplexity.baseUrl,
    defaultModel: String = DecisionProviderSettings.perplexity.defaultModel,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    asOpenAI(perplexity(apiKey, baseUrl, defaultModel, timeouts), imageInput = true)

  private def baseUrlFromEnv: String = envOrElse(baseUrlEnvKey, defaultBaseUrl)

  private def defaultModelFromEnv: String =
    envOrElse(defaultModelEnvKey, TypeSafeServiceConsts.defaultModel)

  private def envOrElse(
    key: String,
    default: String
  ): String =
    Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty).getOrElse(default)
}
