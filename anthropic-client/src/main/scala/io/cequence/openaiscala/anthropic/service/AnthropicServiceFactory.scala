package io.cequence.openaiscala.anthropic.service

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.impl.{
  AnthropicBedrockServiceImpl,
  AnthropicServiceImpl,
  BedrockConnectionSettings,
  BedrockStsClient,
  OpenAIAnthropicChatCompletionService
}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.domain.{RichResponse, WsRequestContext}
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtra}

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

/**
 * Factory for creating [[AnthropicService]] instances (direct Anthropic API or
 * Anthropic-on-Bedrock) and OpenAI-shaped adapters
 * ([[io.cequence.openaiscala.service.OpenAIChatCompletionService]] /
 * [[OpenAIChatCompletionStreamedService]]) over them.
 *
 * '''Direct Anthropic API''' — [[apply]] / [[asOpenAI]]: x-api-key header. Reads
 * `ANTHROPIC_API_KEY` by default.
 *
 * '''Anthropic on AWS Bedrock''' — three mutually-exclusive auth modes; each has both an
 * [[AnthropicService]] variant (`forBedrock*`) and an OpenAI adapter variant
 * (`bedrockAsOpenAI*`):
 *
 *   - '''SigV4''' — [[forBedrock]] / [[bedrockAsOpenAI]]: caller supplies access key + secret
 *     + region (and optionally a session token). Works with both long-lived IAM user creds
 *     (AKIA-prefixed) and externally-minted STS triples (ASIA-prefixed + session token, e.g.
 *     from `aws sts get-session-token` or AWS infra env vars). Reads `AWS_BEDROCK_ACCESS_KEY`
 *     / `AWS_BEDROCK_SECRET_KEY` / `AWS_BEDROCK_REGION` / `AWS_SESSION_TOKEN` by default.
 *   - '''SigV4 + in-process STS minting''' — [[forBedrockWithSessionToken]] /
 *     [[bedrockAsOpenAIWithSessionToken]]: caller supplies long-lived IAM user creds; the
 *     factory calls STS:GetSessionToken on construction (using the same SigV4 signer with
 *     `service = "sts"`) to mint a short-lived ASIA triple. No AWS CLI/SDK dependency. Useful
 *     when you want short-lived creds without running `aws sts get-session-token` externally.
 *   - '''Bearer token''' — [[forBedrockWithBearerToken]] / [[bedrockAsOpenAIWithBearerToken]]:
 *     caller supplies a Bedrock API key. SigV4 is bypassed entirely — the token rides as
 *     `Authorization: Bearer &lt;token&gt;`. Reads `AWS_BEARER_TOKEN_BEDROCK` /
 *     `AWS_BEDROCK_REGION` by default.
 *
 * See [[EnvKeys]] for the full list of env vars consulted, and
 * [[io.cequence.openaiscala.anthropic.service.impl.BedrockConnectionSettings]] for the
 * underlying connection model.
 */
object AnthropicServiceFactory extends AnthropicServiceConsts with EnvHelper {

  private def apiVersion = "2023-06-01"

  /**
   * Environment variable names read by the Bedrock factory methods when the corresponding
   * argument is not supplied explicitly.
   */
  object EnvKeys {

    /** Anthropic-direct API key (used by [[asOpenAI]] / [[apply]]). */
    val anthropicAPIKey = "ANTHROPIC_API_KEY"

    /**
     * AWS access key used for SigV4-signed Bedrock requests. Either the long-lived IAM user
     * key (AKIA-prefixed) or, paired with [[bedrockSessionToken]], the temporary STS key
     * (ASIA-prefixed).
     */
    val bedrockAccessKey = "AWS_BEDROCK_ACCESS_KEY"

    /** Secret matching [[bedrockAccessKey]]. */
    val bedrockSecretKey = "AWS_BEDROCK_SECRET_KEY"

    /** AWS region for Bedrock (e.g. us-east-1, eu-central-1). */
    val bedrockRegion = "AWS_BEDROCK_REGION"

    /**
     * Standard AWS env var holding an STS session token. Set externally — either via `aws sts
     * get-session-token` for local dev, or auto-injected by AWS infra (EC2 / ECS / EKS /
     * Lambda) when an IAM role is attached. When set, the factory threads it into SigV4 as the
     * `X-Amz-Security-Token` header alongside the access/secret pair.
     */
    val bedrockSessionToken = "AWS_SESSION_TOKEN"

    /**
     * Bedrock API key (long-lived or short-lived). Per AWS convention. When set, SigV4 is
     * bypassed entirely — the token rides as `Authorization: Bearer &lt;token&gt;` and
     * access/secret/sessionToken are not needed.
     */
    val bedrockBearerToken = "AWS_BEARER_TOKEN_BEDROCK"
  }

  private val anthropicBetaHeaders = Seq(
    "structured-outputs-2025-11-13",
    "output-128k-2025-02-19",
    "files-api-2025-04-14",
    "code-execution-2025-08-25",
    "mcp-client-2025-04-04",
    "web-fetch-2025-09-10",
    "context-1m-2025-08-07", // deprecated (April 30, 2026)
    "fast-mode-2026-02-01"
  )

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the AnthropicService
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @param materializer
   * @return
   */
  def asOpenAI(
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    timeouts: Option[Timeouts] = None,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory(apiKey, timeouts, withPdf = false, withCache)
    )

  // -----------------------------------------------------------------------------
  // Bedrock auth — three distinct modes, each with both an [[AnthropicService]] and an
  // [[OpenAIChatCompletionStreamedService]] adapter variant:
  //
  //   1. SigV4 (default) — [[forBedrock]] / [[bedrockAsOpenAI]]
  //      Pass an access key + secret + region (and optionally a session token). Works
  //      with both long-lived IAM user creds (AKIA + secret) and externally-minted STS
  //      triples (ASIA + secret + sessionToken). Reads AWS_BEDROCK_ACCESS_KEY,
  //      AWS_BEDROCK_SECRET_KEY, AWS_BEDROCK_REGION, AWS_SESSION_TOKEN by default.
  //
  //   2. SigV4 with in-process STS minting — [[forBedrockWithSessionToken]] /
  //      [[bedrockAsOpenAIWithSessionToken]]
  //      Pass long-lived IAM user creds; the factory calls STS:GetSessionToken on
  //      construction (using the same SigV4 signer with `service = "sts"`) to mint a
  //      short-lived ASIA-prefixed triple, then uses it for Bedrock calls. No AWS CLI
  //      or SDK dependency. Useful when you want short-lived creds without the caller
  //      having to run `aws sts get-session-token` themselves.
  //
  //   3. Bearer token — [[forBedrockWithBearerToken]] / [[bedrockAsOpenAIWithBearerToken]]
  //      Pass a Bedrock API key. SigV4 is bypassed entirely — the token is sent as
  //      `Authorization: Bearer &lt;token&gt;`. Reads AWS_BEARER_TOKEN_BEDROCK and
  //      AWS_BEDROCK_REGION by default.
  // -----------------------------------------------------------------------------

  /**
   * OpenAI adapter for Anthropic Bedrock with SigV4-signed auth. Use this when you have:
   *   - long-lived IAM user creds (AKIA + secret), or
   *   - an externally-minted STS triple (ASIA + secret + sessionToken — e.g. from `aws sts
   *     get-session-token`, or from AWS infra env vars when a role is attached).
   *
   * @param accessKey
   *   AWS access key. Either AKIA-prefixed (long-lived) or ASIA-prefixed (STS temporary).
   *   Defaults to AWS_BEDROCK_ACCESS_KEY env var.
   * @param secretKey
   *   Secret matching `accessKey`. Defaults to AWS_BEDROCK_SECRET_KEY env var.
   * @param region
   *   AWS region for Bedrock. Defaults to AWS_BEDROCK_REGION env var.
   * @param inferenceProfilePrefix
   *   Optional cross-region inference profile prefix (e.g. "eu." or "us."). When set, it's
   *   prepended to the model id on each request unless the model id already starts with it.
   * @param timeouts
   *   Optional explicit HTTP timeouts.
   * @param sessionToken
   *   Optional STS session token. When provided (typically because `accessKey` is ASIA-
   *   prefixed), it rides as `X-Amz-Security-Token` in SigV4 signed headers. Defaults to the
   *   AWS_SESSION_TOKEN env var if set, otherwise None.
   */
  def bedrockAsOpenAI(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None,
    sessionToken: Option[String] = Option(System.getenv(EnvKeys.bedrockSessionToken))
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory.forBedrock(
        accessKey,
        secretKey,
        region,
        inferenceProfilePrefix,
        timeouts,
        sessionToken
      )
    )

  /**
   * OpenAI adapter for Anthropic Bedrock using a Bedrock API key (bearer token). SigV4 signing
   * is bypassed entirely — the token is sent as `Authorization: Bearer &lt;token&gt;`, so no
   * access key, secret key, or session token is needed.
   *
   * @param bearerToken
   *   Bedrock API key. Defaults to AWS_BEARER_TOKEN_BEDROCK env var.
   * @param region
   *   AWS region for Bedrock. Defaults to AWS_BEDROCK_REGION env var.
   */
  def bedrockAsOpenAIWithBearerToken(
    bearerToken: String = getEnvValue(EnvKeys.bedrockBearerToken),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory.forBedrockWithBearerToken(
        bearerToken,
        region,
        inferenceProfilePrefix,
        timeouts
      )
    )

  /**
   * Create a new instance of the [[AnthropicService]]
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @param materializer
   * @return
   */
  def apply(
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    val authHeaders = anthropicBetaHeaders.map { betaHeader =>
      ("anthropic-beta", betaHeader)
    } ++ Seq(
      ("x-api-key", s"$apiKey"),
      ("anthropic-version", apiVersion)
      // TODO: revisit these
    ) ++ (if (withPdf) Seq(("anthropic-beta", "pdfs-2024-09-25")) else Seq.empty) ++
      (if (withCache) Seq(("anthropic-beta", "prompt-caching-2024-07-31")) else Seq.empty)

    new AnthropicServiceClassImpl(defaultCoreUrl, authHeaders, timeouts)
  }

  /**
   * [[AnthropicService]] for Bedrock with SigV4-signed auth. See [[bedrockAsOpenAI]] for
   * parameter semantics — this is the same flow without the OpenAI adapter wrapper.
   */
  def forBedrock(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None,
    sessionToken: Option[String] = Option(System.getenv(EnvKeys.bedrockSessionToken))
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService =
    new AnthropicBedrockServiceClassImpl(
      BedrockConnectionSettings(
        accessKey,
        secretKey,
        region,
        inferenceProfilePrefix,
        sessionToken = sessionToken
      ),
      timeouts
    )

  /**
   * [[AnthropicService]] for Bedrock that mints temporary credentials in-process via
   * STS:GetSessionToken from a long-lived IAM user access/secret pair. The minted triple
   * (ASIA-prefixed key + secret + session token) is then used for SigV4-signed Bedrock calls.
   * Equivalent to running `aws sts get-session-token` externally and feeding the result into
   * [[forBedrock]] — but without depending on the AWS CLI or SDK.
   *
   * Note: the returned service is bound to credentials with a finite lifetime (default 1 hour,
   * max 36 hours per STS limits). For long-running services that outlive the token, recreate
   * the service periodically.
   *
   * @param accessKey
   *   Long-lived IAM user access key (AKIA-prefixed). Defaults to AWS_BEDROCK_ACCESS_KEY.
   * @param secretKey
   *   Long-lived IAM user secret. Defaults to AWS_BEDROCK_SECRET_KEY.
   * @param region
   *   AWS region for Bedrock. Defaults to AWS_BEDROCK_REGION.
   * @param durationSeconds
   *   STS session token lifetime in seconds (900 - 129600 per AWS). Default: 3600 (1 hour).
   */
  def forBedrockWithSessionToken(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    durationSeconds: Int = 3600,
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    val sts = BedrockStsClient.getSessionToken(accessKey, secretKey, durationSeconds)
    new AnthropicBedrockServiceClassImpl(
      BedrockConnectionSettings(
        accessKey = sts.accessKeyId,
        secretKey = sts.secretAccessKey,
        region = region,
        inferenceProfilePrefix = inferenceProfilePrefix,
        sessionToken = Some(sts.sessionToken)
      ),
      timeouts
    )
  }

  /**
   * OpenAI adapter for Anthropic Bedrock that mints temporary STS credentials in-process from
   * a long-lived IAM user access/secret pair. See [[forBedrockWithSessionToken]] for parameter
   * semantics and lifetime caveats.
   */
  def bedrockAsOpenAIWithSessionToken(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    durationSeconds: Int = 3600,
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory.forBedrockWithSessionToken(
        accessKey,
        secretKey,
        region,
        durationSeconds,
        inferenceProfilePrefix,
        timeouts
      )
    )

  /**
   * [[AnthropicService]] for Bedrock using a Bedrock API key (bearer token). SigV4 signing is
   * bypassed entirely — the token is sent as `Authorization: Bearer &lt;token&gt;`, so no
   * access key, secret key, or session token is needed.
   *
   * @param bearerToken
   *   Bedrock API key. Defaults to AWS_BEARER_TOKEN_BEDROCK env var.
   * @param region
   *   AWS region for Bedrock. Defaults to AWS_BEDROCK_REGION env var.
   * @param inferenceProfilePrefix
   *   Optional cross-region inference profile prefix (e.g. "eu." or "us.").
   * @param timeouts
   *   Optional explicit HTTP timeouts.
   */
  def forBedrockWithBearerToken(
    bearerToken: String = getEnvValue(EnvKeys.bedrockBearerToken),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService =
    new AnthropicBedrockServiceClassImpl(
      BedrockConnectionSettings(
        accessKey = "",
        secretKey = "",
        region = region,
        inferenceProfilePrefix = inferenceProfilePrefix,
        bearerToken = Some(bearerToken)
      ),
      timeouts
    )

  private class AnthropicServiceClassImpl(
    coreUrl: String,
    authHeaders: Seq[(String, String)],
    explTimeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends AnthropicServiceImpl {
    // Play WS engine
    override protected val engine: WSClientEngine with WSClientOutputStreamExtra =
      PlayWSStreamClientEngine(
        coreUrl,
        WsRequestContext(authHeaders = authHeaders, explTimeouts = explTimeouts),
        recoverErrors
      )
  }

  private class AnthropicBedrockServiceClassImpl(
    override val connectionInfo: BedrockConnectionSettings,
    explTimeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends AnthropicBedrockServiceImpl {

    // Play WS engine
    override protected val engine: WSClientEngine with WSClientOutputStreamExtra =
      PlayWSStreamClientEngine(
        coreUrl = bedrockCoreUrl(connectionInfo.region),
        WsRequestContext(explTimeouts = explTimeouts),
        recoverErrors
      )

    private def withInferenceProfile(
      settings: AnthropicCreateMessageSettings
    ): AnthropicCreateMessageSettings =
      connectionInfo.inferenceProfilePrefix match {
        case Some(prefix) if !settings.model.startsWith(prefix) =>
          settings.copy(model = prefix + settings.model)
        case _ => settings
      }

    override def createMessage(
      messages: Seq[Message],
      settings: AnthropicCreateMessageSettings
    ): Future[CreateMessageResponse] =
      super.createMessage(messages, withInferenceProfile(settings))

    override def createMessageStreamed(
      messages: Seq[Message],
      settings: AnthropicCreateMessageSettings
    ): Source[ContentBlockDelta, NotUsed] =
      super.createMessageStreamed(messages, withInferenceProfile(settings))
  }

  private def recoverErrors: String => PartialFunction[Throwable, RichResponse] = {
    (serviceEndPointName: String) =>
      {
        case e: TimeoutException =>
          throw new AnthropicScalaClientTimeoutException(
            s"${serviceEndPointName} timed out: ${e.getMessage}."
          )
        case e: UnknownHostException =>
          throw new AnthropicScalaClientUnknownHostException(
            s"${serviceEndPointName} cannot resolve a host name: ${e.getMessage}."
          )
      }
  }
}
