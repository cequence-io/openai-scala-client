package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIDecisionsService}
import io.cequence.openaiscala.typesafe.domain.DecisionProvider
import io.cequence.openaiscala.typesafe.service.impl.{
  OpenAIDecisionsOverTypeSafe,
  OpenAITypeSafeChatCompletionService,
  TypeSafeServiceImpl
}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.Timeouts

import org.slf4j.LoggerFactory

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
 *
 * val local = TypeSafeServiceFactory(DecisionProviderSettings.llamaCpp) // llama-server, no key
 * }}}
 *
 * Their errors are the same [[TypeSafeScalaClientException]] hierarchy, classified by HTTP
 * status. [[liquid]] and [[perplexity]] (and their `WithEngine` / `AsOpenAI` forms) are
 * shorthands for the Liquid AI and Perplexity providers.
 */
object TypeSafeServiceFactory extends EnvHelper {

  private val logger = LoggerFactory.getLogger("TypeSafeServiceFactory")

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
   * The OpenAI adapter over an EXISTING service, saying whether its host reads images -
   * `imageInput = true` for a Perplexity, OpenAI, Liquid (paid `d1`) or llama.cpp service
   * (e.g. wrapped in the retry adapter; the provider's `readsImages`): the image parts of user
   * messages then go into the state instead of being refused. TypeSafe's Jev reads an image
   * part as text, so keep it `false` for it.
   */
  def asOpenAI(
    service: TypeSafeService,
    imageInput: Boolean
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    new OpenAITypeSafeChatCompletionService(service, imageInput)

  /**
   * A host of decision models behind OpenAI's Decisions API interface - `createDecision`, as
   * the full `OpenAIService` has it - so code written against that interface switches between
   * OpenAI and the System One hosts by its construction alone:
   *
   * {{{
   * val decisions: OpenAIDecisionsService =
   *   if (useJev) TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.typeSafe)
   *   else OpenAIServiceFactory()
   *
   * decisions.createDecision(DecisionInput.Text(ticket), questions)   // no model: the host's default
   * }}}
   *
   * On a System One host the questions are translated: a predicate -> a noul, a choice -> a
   * choice (a boolean value as its text), a score -> a score; unnamed questions get keys of
   * their own; the answers come back in the questions' order with their names, values and
   * level labels, a declined question as a refusal. The `safety_identifier` and image
   * `detail`s do not carry over (dropped with a warning), images only to a host that reads
   * them. OpenAI's own host (`DecisionProviderSettings.openAI`) takes the call as it is - also
   * behind the retry adapter (`TypeSafeServiceAdapters.retry`), which serves the interface of
   * the service it wraps. Failures are the `OpenAIScala*` exceptions, the native one as the
   * cause.
   *
   * The other way round - code written against [[TypeSafeService]] on OpenAI's Decisions API -
   * is `TypeSafeServiceFactory(DecisionProviderSettings.openAI)`.
   */
  def asOpenAIDecisions(
    provider: DecisionProvider
  )(
    implicit ec: ExecutionContext
  ): OpenAIDecisionsService =
    asOpenAIDecisions(apply(provider))

  /**
   * [[asOpenAIDecisions(provider* asOpenAIDecisions(provider)]] over an EXISTING service: one
   * of this factory serves the interface itself (it knows its host), and so does the retry
   * adapter over one. Any other service is asked in System One's terms - a translation, even
   * when its host is OpenAI's (then without the `safety_identifier`, image details and the
   * message structure) - and without images.
   */
  def asOpenAIDecisions(
    service: TypeSafeService
  )(
    implicit ec: ExecutionContext
  ): OpenAIDecisionsService =
    asOpenAIDecisions(service, imageInput = false)

  /**
   * [[asOpenAIDecisions(service* asOpenAIDecisions(service)]], saying whether a wrapped
   * service's host reads images. A service that serves the interface itself (one of this
   * factory, or the retry adapter over one) knows that from its provider, so `imageInput` is
   * ignored for it - with a warning when it is set.
   */
  def asOpenAIDecisions(
    service: TypeSafeService,
    imageInput: Boolean
  )(
    implicit ec: ExecutionContext
  ): OpenAIDecisionsService =
    service match {
      case served: OpenAIDecisionsService =>
        if (imageInput)
          logger.warn(
            "imageInput is ignored: the service serves OpenAI's Decisions interface itself " +
              "and reads images as its provider says (DecisionProvider.images)."
          )
        served
      case other => new OpenAIDecisionsOverTypeSafe(other, imageInput)
    }

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
   * reads images (`provider.readsImages`).
   */
  def asOpenAI(
    provider: DecisionProvider
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    asOpenAI(apply(provider), provider.readsImages)

  /**
   * Liquid AI's decision model d1 on its System One API (`https://api.liquid.ai/decisions`,
   * `LIQUID_API_KEY`, `d1:free`), on its own PRIVATE engine. The paid `d1` also reads images
   * (parts in the state, sent in a top-level `images` array - see
   * `DecisionProviderSettings.liquid`).
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
   * output only, exactly like [[asOpenAI]] (`d1:free` and `d1` are in
   * `models-supporting-json-schema`); the image content of user messages goes to the paid `d1`
   * (`d1:free` refuses it with a 422).
   */
  def liquidAsOpenAI(
    apiKey: String = getEnvValue(liquidApiKeyEnvKey),
    baseUrl: String = liquidBaseUrl,
    defaultModel: String = liquidDefaultModel,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    asOpenAI(liquid(apiKey, baseUrl, defaultModel, timeouts), imageInput = true)

  /**
   * Microsoft-Decision-1 on your Microsoft Foundry resource (public preview since 2026-10-09;
   * TypeSafe's protocol at `<endpoint>/providers/microsoft/v1/systemone`, live-verified - see
   * `DecisionProviderSettings.microsoftFoundry`), on its own PRIVATE engine. The key from
   * `FOUNDRY_API_KEY`, the resource's endpoint (`https://<resource>.services.ai.azure.com`)
   * from `FOUNDRY_BASE_URL` - the names of Microsoft's launch example. A request names a
   * DEPLOYMENT of the model, so `deployment` is the default model: `FOUNDRY_MODEL` when set,
   * else `decision-1` (`DecisionProviderSettings.FoundryDefaultDeployment`) - deploy the model
   * under that name, or pass yours (Foundry's proposed `Microsoft-Decision-1` is refused by
   * Azure's reserved-word rule). `listModels` lists the resource's deployments of the model.
   */
  def microsoftFoundry(
    apiKey: String = getEnvValue(foundryApiKeyEnvKey),
    baseUrl: String = getEnvValue(foundryBaseUrlEnvKey),
    deployment: String = foundryDeploymentFromEnv,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService = {
    val provider = DecisionProviderSettings.microsoftFoundry(baseUrl, deployment)
    new TypeSafeServiceImpl(
      apiKey,
      provider.baseUrl,
      provider.defaultModel,
      timeouts,
      provider = provider
    )
  }

  /** [[microsoftFoundry]] on a CALLER-SUPPLIED, shared engine (see [[withEngine]]). */
  def microsoftFoundryWithEngine(
    engine: WSClientEngine,
    apiKey: String = getEnvValue(foundryApiKeyEnvKey),
    baseUrl: String = getEnvValue(foundryBaseUrlEnvKey),
    deployment: String = foundryDeploymentFromEnv
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService = {
    val provider = DecisionProviderSettings.microsoftFoundry(baseUrl, deployment)
    new TypeSafeServiceImpl(
      apiKey,
      provider.baseUrl,
      provider.defaultModel,
      externalEngine = Some(engine),
      provider = provider
    )
  }

  /**
   * Microsoft-Decision-1 behind the OpenAI chat-completion interface - `json_schema`
   * structured output only, exactly like [[asOpenAI]]. The chat model is the deployment name,
   * which `models-supporting-json-schema` lists only as the `-microsoft-decision-1` suffix (a
   * deployment `prod-microsoft-decision-1` matches), so for another name
   * `createChatCompletionWithJSON` either takes `jsonSchemaModels = Seq(deployment)` or falls
   * back to JSON-object mode, which the adapter reads the schema back from - the same
   * questions either way.
   */
  def microsoftFoundryAsOpenAI(
    apiKey: String = getEnvValue(foundryApiKeyEnvKey),
    baseUrl: String = getEnvValue(foundryBaseUrlEnvKey),
    deployment: String = foundryDeploymentFromEnv,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    asOpenAI(microsoftFoundry(apiKey, baseUrl, deployment, timeouts))

  private val foundryApiKeyEnvKey = "FOUNDRY_API_KEY"
  private val foundryBaseUrlEnvKey = "FOUNDRY_BASE_URL"
  private val foundryModelEnvKey = "FOUNDRY_MODEL"

  // Microsoft's launch example reads the deployment from FOUNDRY_MODEL
  private def foundryDeploymentFromEnv: String =
    Option(System.getenv(foundryModelEnvKey))
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(DecisionProviderSettings.FoundryDefaultDeployment)

  /**
   * Perplexity's decision model `pplx-decider-v1.1-27b` (the 2026-10-06 update; the launch
   * model `pplx-decider-v1-27b` is served too) on its Decisions API (`POST
   * https://api.perplexity.ai/v1/decisions`; the key from `PERPLEXITY_API_KEY`, else
   * `SONAR_API_KEY`), on its own PRIVATE engine. The same questions and answers as System One,
   * plus images: an OpenAI-style `image_url` part anywhere in the state, as a base64 PNG, JPEG
   * or WebP data URL (checked before sending) of any size - the API scales an image to about
   * 2,100 tokens (live 2026-10-08). Limits of its own: at most 128 questions and an input
   * under 262,144 tokens. `listModels` returns the two models without a request (Perplexity's
   * `/v1/models` lists its Agent API models).
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
   * (`ImageURLContent` with a data URL) go into the state (both decider ids are in
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
