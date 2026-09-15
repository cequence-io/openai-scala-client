package io.cequence.openaiscala.service

import com.typesafe.config.Config
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.aws.{AwsCredentialsProvider, SigningWSClientEngine}
import io.cequence.wsclient.ConfigImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.TransportSettings
import io.cequence.wsclient.service.ws.Timeouts

import scala.util.control.NonFatal

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
   * Create an OpenAI-compatible service backed by Amazon Bedrock.
   *
   * Authentication and host are independent choices:
   *   - `auth` is either a Bedrock API key sent as a bearer token
   *     ([[BedrockAuth.BearerToken]]) or an IAM access key and secret signed per request
   *     ([[BedrockAuth.SigV4]]). The default, [[BedrockAuth.fromEnv]], takes the bearer token
   *     when one is in the environment and falls back to SigV4 credentials - the usual "API
   *     key in development, IAM role in production" split, with no branching at the call site.
   *   - `endpoint` selects the host: [[BedrockEndpoint.Mantle]] (default) or
   *     [[BedrockEndpoint.Runtime]], which additionally accepts the cross-region
   *     inference-profile model ids (`us.openai.*`, `global.openai.*`).
   *
   * Note that not all endpoints of the full OpenAI service are available on Bedrock -
   * primarily the Responses API (`createModelResponse`), Chat Completions and the Models API
   * (`listModels`). See <a
   * href="https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html">the
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
   * '''Under SigV4''', multipart and raw-file endpoints (file upload, image edit/variation,
   * audio transcription) fail fast - their bytes are produced inside the HTTP engine and
   * cannot be hashed beforehand. The Bedrock OpenAI-compatible surface does not serve them
   * anyway.
   *
   * @param region
   *   AWS region, e.g. "us-east-2". Defaults to the `AWS_BEDROCK_REGION` env var.
   * @param isOpenAIModel
   *   the OpenAI provider models (e.g. `openai.gpt-5.6-luna`) are served from the `openai/v1`
   *   base path; set this to `true` for them. All other models (e.g. the gpt-oss family) use
   *   the standard `v1` base path, so leave the default `false`. Ignored for
   *   [[BedrockEndpoint.Runtime]], which is always `openai/v1`.
   */
  def forBedrock(
    auth: BedrockAuth = BedrockAuth.fromEnv(),
    region: String = getEnvOrThrow(bedrockRegionEnvKey),
    endpoint: BedrockEndpoint = BedrockEndpoint.Mantle,
    isOpenAIModel: Boolean = false,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F = {
    val coreUrl = endpoint.coreUrl(region, isOpenAIModel)

    auth match {
      case BedrockAuth.BearerToken(apiKey) =>
        customInstance(coreUrl, bearerTokenContext(apiKey), timeouts)

      case BedrockAuth.SigV4(credentials) =>
        forAwsSigV4Custom(coreUrl, credentials, region, timeouts = timeouts)
    }
  }

  /**
   * [[forBedrock]] on a CALLER-SUPPLIED, shared engine - see [[customEngineInstance]] for the
   * shared-engine semantics. Closing the returned service never closes `engine`, under either
   * form of authentication.
   */
  def forBedrockWithEngine(
    engine: WSClientEngine,
    auth: BedrockAuth = BedrockAuth.fromEnv(),
    region: String = getEnvOrThrow(bedrockRegionEnvKey),
    endpoint: BedrockEndpoint = BedrockEndpoint.Mantle,
    isOpenAIModel: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): F = {
    val coreUrl = endpoint.coreUrl(region, isOpenAIModel)

    auth match {
      case BedrockAuth.BearerToken(apiKey) =>
        customEngineInstance(engine, coreUrl, bearerTokenContext(apiKey))

      case BedrockAuth.SigV4(credentials) =>
        customEngineInstance(
          awsSigningEngine(engine, credentials, region),
          coreUrl,
          // an EMPTY request context - see forAwsSigV4Custom
          WsRequestContext()
        )
    }
  }

  private def bearerTokenContext(apiKey: String): WsRequestContext =
    WsRequestContext(authHeaders = Seq(("Authorization", s"Bearer $apiKey")))

  private def getEnvOrThrow(envKey: String): String =
    Option(System.getenv(envKey)).getOrElse(
      throw new OpenAIScalaClientException(
        s"Environment variable '$envKey' is not set. " +
          "Please set it or provide the value explicitly."
      )
    )

  /**
   * [[forBedrock]] with SigV4 auth pointed at an explicit base URL - e.g. a PrivateLink / VPC
   * endpoint or a gateway that fronts Bedrock - with an explicit AWS signing scope.
   */
  def forAwsSigV4Custom(
    coreUrl: String,
    credentials: AwsCredentialsProvider = AwsCredentialsProvider.fromEnv(),
    region: String = getEnvOrThrow(bedrockRegionEnvKey),
    service: String = "bedrock",
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): F = {
    val signing = awsSigningEngine(
      newPrivateEngine(timeouts),
      credentials,
      region,
      service,
      ownsUnderlying = true
    )

    // an EMPTY request context: the engine appends per-call extraHeaders to authHeaders, so a
    // static Authorization here would ride alongside the AWS4-HMAC-SHA256 one
    try ownedEngineInstance(signing, coreUrl, WsRequestContext())
    catch {
      case NonFatal(e) =>
        // the engine was built for this service only - do not orphan its actor system
        signing.close()
        throw e
    }
  }

  /** [[forAwsSigV4Custom]] on a CALLER-SUPPLIED, shared engine, which it never closes. */
  def forAwsSigV4CustomWithEngine(
    engine: WSClientEngine,
    coreUrl: String,
    credentials: AwsCredentialsProvider = AwsCredentialsProvider.fromEnv(),
    region: String = getEnvOrThrow(bedrockRegionEnvKey),
    service: String = "bedrock"
  )(
    implicit ec: ExecutionContext
  ): F =
    customEngineInstance(
      awsSigningEngine(engine, credentials, region, service),
      coreUrl,
      WsRequestContext()
    )

  /**
   * The engine a private-engine entry point creates. The default is the classpath-discovered
   * (non-streaming) engine; the streaming factories override it with a streaming one.
   */
  protected def newPrivateEngine(
    timeouts: Option[Timeouts]
  )(
    implicit ec: ExecutionContext
  ): WSClientEngine =
    ProjectWSClientEngine(TransportSettings(timeouts = timeouts.getOrElse(Timeouts())))

  /**
   * Like [[customEngineInstance]] but the resulting service OWNS `engine` and closes it. The
   * default does not take ownership; factories whose service impl can own an engine override
   * this.
   */
  protected def ownedEngineInstance(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext
  )(
    implicit ec: ExecutionContext
  ): F = customEngineInstance(engine, coreUrl, requestContext)

  /**
   * The signing decorator. `ownsUnderlying = false` by default: a shared engine must survive
   * the service that borrowed it.
   */
  private def awsSigningEngine(
    underlying: WSClientEngine,
    credentials: AwsCredentialsProvider,
    region: String,
    service: String = "bedrock",
    ownsUnderlying: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): WSClientEngine =
    SigningWSClientEngine(
      underlying = underlying,
      credentials = credentials,
      region = region,
      awsService = service,
      ownsUnderlying = ownsUnderlying
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
