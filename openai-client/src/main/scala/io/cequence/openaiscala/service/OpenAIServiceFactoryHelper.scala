package io.cequence.openaiscala.service

import com.typesafe.config.Config
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.ConfigImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

trait OpenAIServiceFactoryHelper[F] extends OpenAIServiceConsts with HasOpenAIConfig {

  def apply(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F = {
    val orgIdHeader = orgId.map(("OpenAI-Organization", _))
    val authHeaders = orgIdHeader ++: Seq(
      ("Authorization", s"Bearer $apiKey"),
      ("OpenAI-Beta", "assistants=v2")
    )

    customInstance(defaultCoreUrl, WsRequestContext(authHeaders, Nil), timeouts)
  }

  def apply(
  )(
    implicit ec: ExecutionContext
  ): F =
    apply(clientConfig)

  def apply(
    config: Config
  )(
    implicit ec: ExecutionContext
  ): F = {
    def intTimeoutAux(fieldName: String) =
      config.optionalInt(s"$configPrefix.timeouts.${fieldName}Sec").map(_ * 1000)

    val timeouts = Timeouts(
      requestTimeout = intTimeoutAux("requestTimeout"),
      readTimeout = intTimeoutAux("readTimeout"),
      connectTimeout = intTimeoutAux("connectTimeout"),
      pooledConnectionIdleTimeout = intTimeoutAux("pooledConnectionIdleTimeout")
    )

    val apiKey = config
      .optionalString(s"$configPrefix.apiKey")
      .getOrElse(
        throw new OpenAIScalaClientException(
          s"API key is not defined in the config at '$configPrefix.apiKey'. " +
            "Please set the OPENAI_SCALA_CLIENT_API_KEY environment variable or provide an API key explicitly."
        )
      )

    apply(
      apiKey = apiKey,
      orgId = config.optionalString(s"$configPrefix.orgId"),
      timeouts = timeouts.toOption
    )
  }

  /**
   * Create an OpenAI Service for Azure using an API key.
   *
   * Note that not all endpoints are supported! Check <a
   * href="https://learn.microsoft.com/en-us/azure/cognitive-services/openai/reference">the
   * Azure OpenAI API documentation</a> for more information.
   *
   * @param resourceName
   *   The name of your Azure OpenAI Resource.
   * @param deploymentId
   *   The deployment name you chose when you deployed the model.
   * @param apiVersion
   *   The API version to use for this operation. This follows the YYYY-MM-DD format. Supported
   *   versions: 2023-03-15-preview, 2022-12-01, 2023-05-15, and 2023-06-01-preview
   */
  def forAzureWithApiKey(
    resourceName: String,
    deploymentId: String,
    apiVersion: String,
    apiKey: String,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F = {
    val authHeaders = Seq(("api-key", apiKey))
    forAzureAux(resourceName, deploymentId, apiVersion, authHeaders, timeouts)
  }

  /** Azure OpenAI API-key variant backed by a caller-owned shared engine. */
  def forAzureWithApiKeyAndEngine(
    engine: WSClientEngine,
    resourceName: String,
    deploymentId: String,
    apiVersion: String,
    apiKey: String
  )(
    implicit ec: ExecutionContext
  ): F = {
    val coreUrl =
      s"https://${resourceName}.openai.azure.com/openai/deployments/${deploymentId}/"
    customEngineInstance(
      engine,
      coreUrl,
      WsRequestContext(
        authHeaders = Seq(("api-key", apiKey)),
        extraParams = Seq("api-version" -> apiVersion)
      )
    )
  }

  /**
   * Create an OpenAI Service for Azure using an access token (Azure Active Directory
   * authentication).
   *
   * Note that not all endpoints are supported! Check <a
   * href="https://learn.microsoft.com/en-us/azure/cognitive-services/openai/reference">the
   * Azure OpenAI API documentation</a> for more information.
   *
   * @param resourceName
   *   The name of your Azure OpenAI Resource.
   * @param deploymentId
   *   The deployment name you chose when you deployed the model.
   * @param apiVersion
   *   The API version to use for this operation. This follows the YYYY-MM-DD format. Supported
   *   versions: 2023-03-15-preview, 2022-12-01, 2023-05-15, and 2023-06-01-preview
   */
  @Deprecated
  def forAzureWithAccessToken(
    resourceName: String,
    deploymentId: String,
    apiVersion: String,
    accessToken: String,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F = {
    val authHeaders = Seq(("Authorization", s"Bearer $accessToken"))
    forAzureAux(resourceName, deploymentId, apiVersion, authHeaders, timeouts)
  }

  private def forAzureAux(
    resourceName: String,
    deploymentId: String,
    apiVersion: String,
    authHeaders: Seq[(String, String)],
    timeouts: Option[Timeouts]
  )(
    implicit ec: ExecutionContext
  ): F = {
    val coreUrl =
      s"https://${resourceName}.openai.azure.com/openai/deployments/${deploymentId}/"

    val extraParams = Seq("api-version" -> apiVersion)

    customInstance(
      coreUrl,
      WsRequestContext(
        authHeaders,
        extraParams
      ),
      timeouts
    )
  }

  /**
   * Create an OpenAI Service backed by the Amazon Bedrock `bedrock-mantle` endpoint, which
   * exposes the OpenAI Responses API for models such as `openai.gpt-5.5`, `openai.gpt-5.4`,
   * and the `openai.gpt-oss-*` family.
   *
   * Authentication uses a Bedrock (long-term) API key passed as a bearer token, exactly like
   * the OpenAI SDK configured via `OPENAI_BASE_URL` / `OPENAI_API_KEY`. No AWS SigV4 signing
   * is required (unlike the Anthropic Bedrock `bedrock-runtime` path).
   *
   * Note that not all endpoints of the full OpenAI service are available on `bedrock-mantle` -
   * primarily the Responses API (`createModelResponse`) and the Models API (`listModels`). See
   * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html">the
   * bedrock-mantle documentation</a> for details.
   *
   * '''No Batch API support''' (verified July 2026): `/batches` does not exist on either base
   * path (404), and the Files API - although served - rejects `purpose=batch` ("Only
   * 'fine-tune' is currently supported"). Hence `createBatch` and the
   * `createChatCompletionBatch*` methods compile but fail at runtime (the batch input-file
   * upload is the first step to blow up). To use this service where an
   * [[io.cequence.openaiscala.service.OpenAIChatCompletionBatchService]] is expected, wrap it
   * with `OpenAIServiceAdapters.chatCompletionBatchEmulated` (parallel synchronous calls - no
   * batch discount), or route batch traffic to a natively-batching provider via
   * `chatCompletionBatchRouterMixed`. Beware that `openai.gpt-5.5` additionally rejects
   * `/chat/completions` altogether (Responses API only), so wrap the service in
   * `OpenAIResponsesChatCompletionService` before emulating batches for it.
   *
   * @param apiKey
   *   Bedrock API key (sent as a bearer token). Defaults to the `AWS_BEARER_TOKEN_BEDROCK` env
   *   var.
   * @param region
   *   AWS region, e.g. "us-east-2". Defaults to the `AWS_BEDROCK_REGION` env var.
   * @param isOpenAIModel
   *   The OpenAI provider models (e.g. `openai.gpt-5.5`) are an exception and are served from
   *   the `openai/v1` base path; set this to `true` for them. All other models (e.g. the
   *   gpt-oss family) use the standard `v1` base path, so leave the default `false`.
   */
  def forBedrockMantle(
    apiKey: String = getEnvOrThrow(bedrockMantleBearerTokenEnvKey),
    region: String = getEnvOrThrow(bedrockMantleRegionEnvKey),
    isOpenAIModel: Boolean = false,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F = {
    val basePath =
      if (isOpenAIModel) openAIBedrockMantleBasePath else defaultBedrockMantleBasePath
    val authHeaders = Seq(("Authorization", s"Bearer $apiKey"))
    customInstance(
      bedrockMantleCoreUrl(region, basePath),
      WsRequestContext(authHeaders, Nil),
      timeouts
    )
  }

  /** Amazon Bedrock Mantle variant backed by a caller-owned shared engine. */
  def forBedrockMantleWithEngine(
    engine: WSClientEngine,
    apiKey: String,
    region: String,
    isOpenAIModel: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): F = {
    val basePath =
      if (isOpenAIModel) openAIBedrockMantleBasePath else defaultBedrockMantleBasePath
    customEngineInstance(
      engine,
      bedrockMantleCoreUrl(region, basePath),
      WsRequestContext(authHeaders = Seq(("Authorization", s"Bearer $apiKey")))
    )
  }

  private def getEnvOrThrow(envKey: String): String =
    Option(System.getenv(envKey)).getOrElse(
      throw new OpenAIScalaClientException(
        s"Environment variable '$envKey' is not set. " +
          "Please set it or provide the value explicitly."
      )
    )

  def customInstance(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F

  /**
   * Creates the service on a CALLER-SUPPLIED, SITE-STATELESS engine - ONE engine (one
   * connection pool, one actor system) can back any number of services/providers; the service
   * holds its own site binding (built here from the api key) and feeds it into every call.
   * Closing such a service does NOT close the shared engine - close the engine once, when done
   * with all services using it. Factories producing streaming services require an engine with
   * `Source`-typed output streaming (`WSClientOutputStreamExtraAkka`) and fail fast otherwise.
   */
  def withEngine(
    engine: WSClientEngine,
    apiKey: String = configuredAPIKey,
    orgId: Option[String] = None
  )(
    implicit ec: ExecutionContext
  ): F = {
    val orgIdHeader = orgId.map(("OpenAI-Organization", _))
    val authHeaders = orgIdHeader ++: Seq(
      ("Authorization", s"Bearer $apiKey"),
      ("OpenAI-Beta", "assistants=v2")
    )

    customEngineInstance(engine, defaultCoreUrl, WsRequestContext(authHeaders, Nil))
  }

  /**
   * [[withEngine]] with a fully custom base URL and request context (e.g. an OpenAI-compatible
   * gateway on a shared engine).
   */
  def customEngineInstance(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): F

  protected def configuredAPIKey: String =
    clientConfig
      .optionalString(s"$configPrefix.apiKey")
      .getOrElse(
        throw new OpenAIScalaClientException(
          s"API key is not defined in the config at '$configPrefix.apiKey'. " +
            "Please set the OPENAI_SCALA_CLIENT_API_KEY environment variable or provide an API key explicitly."
        )
      )
}

trait RawWsServiceFactory[F] {

  def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F

  /**
   * Creates the service on a CALLER-SUPPLIED, SITE-STATELESS engine - ONE engine (one
   * connection pool, one actor system) can back any number of services/providers; the service
   * holds its own site binding (from the given core URL and request context) and feeds it into
   * every call. Closing such a service does NOT close the shared engine - close the engine
   * once, when done with all services using it. Factories producing streaming services require
   * an engine with `Source`-typed output streaming (`WSClientOutputStreamExtraAkka`) and fail
   * fast otherwise.
   */
  def withEngine(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): F
}
