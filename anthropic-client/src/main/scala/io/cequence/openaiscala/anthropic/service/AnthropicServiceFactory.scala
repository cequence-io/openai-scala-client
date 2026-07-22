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
import io.cequence.openaiscala.anthropic.domain.managedagents.AgentTool
import io.cequence.openaiscala.anthropic.service.auth.{
  AnthropicOAuthConsts,
  AnthropicOAuthProfileTokenProvider,
  AnthropicTokenProvider
}
import io.cequence.openaiscala.anthropic.service.impl.{
  AnthropicBedrockBatchInferenceServiceImpl,
  AnthropicBedrockServiceImpl,
  AnthropicServiceImpl,
  BedrockConnectionSettings,
  BedrockStsClient,
  OpenAIAnthropicBedrockChatCompletionService,
  OpenAIAnthropicChatCompletionService,
  OpenAIAnthropicManagedAgentChatCompletionService
}
import io.cequence.openaiscala.service.OpenAIChatCompletionBatchService
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.domain.{
  CequenceWSTimeoutException,
  CequenceWSUnknownHostException,
  RichResponse,
  SiteBinding,
  WsRequestContext
}
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}

import java.net.UnknownHostException
import java.nio.file.Path
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
 * '''Anthropic on Bedrock `bedrock-mantle`''' — [[forBedrockMantle]] /
 * [[forBedrockMantleWithEngine]] (caller-owned shared engine): a separate host
 * (`bedrock-mantle.&lt;region&gt;.api.aws`, not `bedrock-runtime`) serving Claude via the
 * Anthropic-native Messages API from the `anthropic/v1` base path, with bearer-token auth and
 * short-form model ids (e.g. `anthropic.claude-haiku-4-5`). No OpenAI adapter variant yet.
 *
 * '''Auth modes''' (direct Anthropic API):
 *
 *   - x-api-key — [[apply]] / [[asOpenAI]]. Reads `ANTHROPIC_API_KEY`.
 *   - Static bearer/OAuth token — [[forAuthToken]] / [[asOpenAIWithAuthToken]] /
 *     [[managedAgentAsOpenAIWithAuthToken]]. Reads `ANTHROPIC_AUTH_TOKEN`, falling back to
 *     `CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE` (a library-scoped var safe to export persistently
 *     \- unlike `CLAUDE_CODE_OAUTH_TOKEN`, the `claude` CLI never reads it), then to
 *     `CLAUDE_CODE_OAUTH_TOKEN` as a last resort.
 *   - `ant auth` OAuth profile with automatic token refresh — [[forOAuthProfile]].
 *   - Custom token provider / fully custom request context — [[forAuthTokenProvider]] /
 *     [[customInstance]].
 *
 * See [[EnvKeys]] for the full list of env vars consulted, and
 * [[io.cequence.openaiscala.anthropic.service.impl.BedrockConnectionSettings]] for the
 * underlying connection model.
 */
object AnthropicServiceFactory extends AnthropicServiceConsts with EnvHelper {

  private def apiVersion = "2023-06-01"

  /**
   * Environment variable names read by the factory methods when the corresponding argument is
   * not supplied explicitly.
   */
  object EnvKeys {

    /** Anthropic-direct API key (used by [[asOpenAI]] / [[apply]]). */
    val anthropicAPIKey = "ANTHROPIC_API_KEY"

    /**
     * OAuth/bearer token sent as `Authorization: Bearer &lt;token&gt;`. Primary source: a
     * platform OAuth token from `ant auth login` (retrievable via `ant auth print-credentials
     * --access-token`), or a gateway-issued bearer token (pass `withOAuthBeta = false` for
     * gateways). Used by [[forAuthToken]] / [[asOpenAIWithAuthToken]] /
     * [[managedAgentAsOpenAIWithAuthToken]].
     */
    val anthropicAuthToken = "ANTHROPIC_AUTH_TOKEN"

    /**
     * Second fallback source for [[anthropicAuthToken]] - a library-scoped env var name that
     * is NOT part of Claude Code's own credential precedence (`ANTHROPIC_API_KEY` /
     * `ANTHROPIC_AUTH_TOKEN` / `apiKeyHelper` / [[claudeCodeOAuthToken]] / subscription
     * `/login`). Setting [[claudeCodeOAuthToken]] persistently (e.g. in `~/.bashrc`) has a
     * real side effect: it also redirects the actual `claude` CLI's OWN interactive sessions
     * onto that token, ranking above subscription `/login` in its precedence chain - meaning
     * you'd need to remember to unset it before using `claude` normally. This var exists so a
     * token can be exported persistently for THIS library only, with zero effect on the
     * `claude` binary - it reads the exact literal `CLAUDE_CODE_OAUTH_TOKEN` only, never a
     * suffixed variant like this one.
     */
    val claudeCodeOAuthTokenAlternative = "CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE"

    /**
     * Last-resort fallback source for [[anthropicAuthToken]]: a long-lived Claude Code
     * subscription token from `claude setup-token`. CAVEAT: these tokens are scoped to the
     * Claude Code backend and are documented as rejected by the public API (expect a 401 here)
     *   - support is best-effort. Note: since 2026-06-15 subscription plans include a monthly
     *     "Agent SDK credit" that sanctions third-party use of these tokens THROUGH the Claude
     *     Agent SDK/CLI harness, but that does not extend to the public REST API this client
     *     calls. Also note this env var is one Claude Code itself reads (see
     *     [[claudeCodeOAuthTokenAlternative]]'s doc) - prefer that var if you want to set a
     *     persistent fallback without affecting your own interactive `claude` sessions.
     */
    val claudeCodeOAuthToken = "CLAUDE_CODE_OAUTH_TOKEN"

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

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the AnthropicService
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def asOpenAI(
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    timeouts: Option[Timeouts] = None,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory(apiKey, timeouts, withPdf = false, withCache)
    )

  /**
   * OpenAI-adapter wrapper for an EXISTING [[AnthropicService]] - e.g. one created with
   * [[withEngine]] on a view bound to a shared transport.
   */
  def asOpenAI(
    service: AnthropicService
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService =
    new OpenAIAnthropicChatCompletionService(service)

  /**
   * Bearer/OAuth alternative to [[asOpenAI]]. See [[forAuthToken]] for the auth-mode
   * semantics.
   *
   * @param authToken
   *   The bearer/OAuth token to use for authentication (if not specified, resolved from
   *   ANTHROPIC_AUTH_TOKEN, then CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE, then
   *   CLAUDE_CODE_OAUTH_TOKEN)
   * @param withOAuthBeta
   *   Whether to send the `oauth-2025-04-20` auth-mode beta header. Set to false for gateway-
   *   issued bearer tokens.
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def asOpenAIWithAuthToken(
    authToken: String = defaultAuthToken,
    withOAuthBeta: Boolean = true,
    timeouts: Option[Timeouts] = None,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService =
    new OpenAIAnthropicChatCompletionService(
      forAuthToken(authToken, withOAuthBeta, timeouts, withPdf = false, withCache)
    )

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] backed by the Anthropic
   * Managed Agents API (agents + sessions), so managed agents can be plugged into standard
   * OpenAI-style workflows (routers, retries, load balancing, ...).
   *
   * Each chat-completion call runs one managed-agent session turn. When `agentId` is empty,
   * agents are created lazily - one per (model, system prompt) combination - and cached;
   * `settings.model` selects the agent's model. When `environmentId` is empty, a cloud
   * environment named `openai-scala-client-chat-adapter` is looked up or created and reused.
   *
   * Requires the `managed-agents-2026-04-01` beta on the API key; not available on Bedrock.
   * See
   * [[io.cequence.openaiscala.anthropic.service.impl.OpenAIAnthropicManagedAgentChatCompletionService]]
   * for the exact semantics and limitations.
   *
   * @param agentId
   *   Use this pre-created agent for all sessions (its configured model applies and
   *   `settings.model` is ignored); when empty, agents are created and cached on the fly.
   * @param environmentId
   *   Run sessions in this environment; when empty, a shared adapter environment is looked up
   *   or created.
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional). Note that a managed-agent turn
   *   can take minutes - configure a generous read timeout.
   * @param agentTools
   *   Tools granted to lazily-created agents (ignored when `agentId` is set). Defaults to the
   *   full built-in toolset (bash, file ops, web search, ...).
   * @param deleteSessionsAfterUse
   *   Whether to delete each session once its turn finishes (default true).
   */
  def managedAgentAsOpenAI(
    agentId: Option[String] = None,
    environmentId: Option[String] = None,
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    timeouts: Option[Timeouts] = None,
    agentTools: Seq[AgentTool] = Seq(AgentTool.Toolset()),
    deleteSessionsAfterUse: Boolean = true
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicManagedAgentChatCompletionService(
      AnthropicServiceFactory(apiKey, timeouts),
      agentId,
      environmentId,
      agentTools,
      deleteSessionsAfterUse
    )

  /**
   * Bearer/OAuth alternative to [[managedAgentAsOpenAI]]. See [[forAuthToken]] for the
   * auth-mode semantics.
   *
   * @param agentId
   *   Use this pre-created agent for all sessions (its configured model applies and
   *   `settings.model` is ignored); when empty, agents are created and cached on the fly.
   * @param environmentId
   *   Run sessions in this environment; when empty, a shared adapter environment is looked up
   *   or created.
   * @param authToken
   *   The bearer/OAuth token to use for authentication (if not specified, resolved from
   *   ANTHROPIC_AUTH_TOKEN, then CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE, then
   *   CLAUDE_CODE_OAUTH_TOKEN)
   * @param withOAuthBeta
   *   Whether to send the `oauth-2025-04-20` auth-mode beta header. Set to false for gateway-
   *   issued bearer tokens.
   * @param timeouts
   *   The explicit timeouts to use for the service (optional). Note that a managed-agent turn
   *   can take minutes - configure a generous read timeout.
   * @param agentTools
   *   Tools granted to lazily-created agents (ignored when `agentId` is set). Defaults to the
   *   full built-in toolset (bash, file ops, web search, ...).
   * @param deleteSessionsAfterUse
   *   Whether to delete each session once its turn finishes (default true).
   */
  def managedAgentAsOpenAIWithAuthToken(
    agentId: Option[String] = None,
    environmentId: Option[String] = None,
    authToken: String = defaultAuthToken,
    withOAuthBeta: Boolean = true,
    timeouts: Option[Timeouts] = None,
    agentTools: Seq[AgentTool] = Seq(AgentTool.Toolset()),
    deleteSessionsAfterUse: Boolean = true
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicManagedAgentChatCompletionService(
      forAuthToken(authToken, withOAuthBeta, timeouts),
      agentId,
      environmentId,
      agentTools,
      deleteSessionsAfterUse
    )

  /**
   * [[managedAgentAsOpenAI]] variant that wraps a pre-built [[AnthropicService]] instead of
   * constructing one from an API key. Use this with services built via [[forOAuthProfile]],
   * [[forAuthTokenProvider]], or [[customInstance]].
   *
   * Note: Scala allows default arguments on only one overload of a given method name, so this
   * overload takes no defaults - see [[managedAgentAsOpenAI]] for the defaulted variant.
   *
   * @param service
   *   The pre-built [[AnthropicService]] to wrap.
   * @param agentId
   *   Use this pre-created agent for all sessions (its configured model applies and
   *   `settings.model` is ignored); when empty, agents are created and cached on the fly.
   * @param environmentId
   *   Run sessions in this environment; when empty, a shared adapter environment is looked up
   *   or created.
   * @param agentTools
   *   Tools granted to lazily-created agents (ignored when `agentId` is set).
   * @param deleteSessionsAfterUse
   *   Whether to delete each session once its turn finishes.
   */
  def managedAgentAsOpenAI(
    service: AnthropicService,
    agentId: Option[String],
    environmentId: Option[String],
    agentTools: Seq[AgentTool],
    deleteSessionsAfterUse: Boolean
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicManagedAgentChatCompletionService(
      service,
      agentId,
      environmentId,
      agentTools,
      deleteSessionsAfterUse
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
   *
   * Note: the returned service has no Bedrock batch support wired in - batch calls fail with a
   * message pointing to [[bedrockAsOpenAIWithBatchSupport]].
   */
  def bedrockAsOpenAI(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None,
    sessionToken: Option[String] = Option(System.getenv(EnvKeys.bedrockSessionToken))
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService = {
    val connectionInfo = BedrockConnectionSettings(
      accessKey,
      secretKey,
      region,
      inferenceProfilePrefix,
      sessionToken = sessionToken
    )

    new OpenAIAnthropicBedrockChatCompletionService(
      new AnthropicBedrockServiceClassImpl(connectionInfo, timeouts),
      connectionInfo,
      batchSupport = None
    )
  }

  /** Bedrock OpenAI adapter backed by a caller-owned shared streaming engine. */
  def bedrockAsOpenAIWithEngine(
    engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    accessKey: String,
    secretKey: String,
    region: String,
    inferenceProfilePrefix: Option[String] = None,
    sessionToken: Option[String] = Option(System.getenv(EnvKeys.bedrockSessionToken))
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService = {
    val connectionInfo = BedrockConnectionSettings(
      accessKey,
      secretKey,
      region,
      inferenceProfilePrefix,
      sessionToken = sessionToken
    )

    new OpenAIAnthropicBedrockChatCompletionService(
      new AnthropicBedrockServiceClassImpl(
        connectionInfo,
        externalEngine = Some(engine)
      ),
      connectionInfo,
      batchSupport = None
    )
  }

  /**
   * OpenAI adapter for Anthropic Bedrock with SigV4-signed auth, additionally wired for
   * Bedrock's own batch inference API (`model-invocation-job`) - distinct from Anthropic's
   * direct Message Batches API, which Bedrock does not expose. Requests are staged as a JSONL
   * file in S3 and results are read back from there; see [[AnthropicBedrockBatchSupport]].
   *
   * @param s3Bucket
   *   S3 bucket used for staging batch inputs and outputs.
   * @param roleArn
   *   ARN of an IAM service role trusted by `bedrock.amazonaws.com`, with permissions to read
   *   and write `s3Bucket`. See <a
   *   href="https://docs.aws.amazon.com/bedrock/latest/userguide/batch-iam-sr.html">Create a
   *   service role for batch inference</a>.
   * @param s3Region
   *   Region of `s3Bucket`. Defaults to `region`.
   * @see
   *   [[bedrockAsOpenAI]] for the auth parameter semantics.
   */
  def bedrockAsOpenAIWithBatchSupport(
    s3Bucket: String,
    roleArn: String,
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    s3Region: Option[String] = None,
    s3PathPrefix: String = "openai-scala-client-batches",
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None,
    sessionToken: Option[String] = Option(System.getenv(EnvKeys.bedrockSessionToken))
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService = {
    val connectionInfo = BedrockConnectionSettings(
      accessKey,
      secretKey,
      region,
      inferenceProfilePrefix,
      sessionToken = sessionToken
    )

    new OpenAIAnthropicBedrockChatCompletionService(
      new AnthropicBedrockServiceClassImpl(connectionInfo, timeouts),
      connectionInfo,
      batchSupport = Some(
        AnthropicBedrockBatchSupport(
          batchService =
            bedrockBatchInference(accessKey, secretKey, region, timeouts, sessionToken),
          s3Bucket = s3Bucket,
          roleArn = roleArn,
          s3Region = s3Region,
          s3PathPrefix = s3PathPrefix
        )
      )
    )
  }

  /** Batch-capable Bedrock adapter backed by one caller-owned shared engine. */
  def bedrockAsOpenAIWithBatchSupportAndEngine(
    engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    s3Bucket: String,
    roleArn: String,
    accessKey: String,
    secretKey: String,
    region: String,
    s3Region: Option[String] = None,
    s3PathPrefix: String = "openai-scala-client-batches",
    inferenceProfilePrefix: Option[String] = None,
    sessionToken: Option[String] = Option(System.getenv(EnvKeys.bedrockSessionToken))
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService = {
    val connectionInfo = BedrockConnectionSettings(
      accessKey,
      secretKey,
      region,
      inferenceProfilePrefix,
      sessionToken = sessionToken
    )

    new OpenAIAnthropicBedrockChatCompletionService(
      new AnthropicBedrockServiceClassImpl(
        connectionInfo,
        externalEngine = Some(engine)
      ),
      connectionInfo,
      batchSupport = Some(
        AnthropicBedrockBatchSupport(
          batchService = new AnthropicBedrockBatchInferenceServiceImpl(
            connectionInfo,
            externalEngine = Some(engine)
          ),
          s3Bucket = s3Bucket,
          roleArn = roleArn,
          s3Region = s3Region,
          s3PathPrefix = s3PathPrefix
        )
      )
    )
  }

  /**
   * Create a new instance of the [[AnthropicBedrockBatchInferenceService]] - a SigV4-signed
   * REST client for Bedrock's own batch inference API (`model-invocation-job`), processed at
   * 50% of standard cost.
   *
   * @param region
   *   AWS region for Bedrock. Defaults to AWS_BEDROCK_REGION env var.
   */
  def bedrockBatchInference(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    timeouts: Option[Timeouts] = None,
    sessionToken: Option[String] = Option(System.getenv(EnvKeys.bedrockSessionToken))
  )(
    implicit ec: ExecutionContext
  ): AnthropicBedrockBatchInferenceService =
    new AnthropicBedrockBatchInferenceServiceImpl(
      BedrockConnectionSettings(accessKey, secretKey, region, sessionToken = sessionToken),
      timeouts
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
   *
   * Note: the returned service has no Bedrock batch support wired in - batch calls fail with a
   * message pointing to [[bedrockAsOpenAIWithBatchSupport]].
   */
  def bedrockAsOpenAIWithBearerToken(
    bearerToken: String = getEnvValue(EnvKeys.bedrockBearerToken),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService = {
    val connectionInfo = BedrockConnectionSettings(
      accessKey = "",
      secretKey = "",
      region = region,
      inferenceProfilePrefix = inferenceProfilePrefix,
      bearerToken = Some(bearerToken)
    )

    new OpenAIAnthropicBedrockChatCompletionService(
      new AnthropicBedrockServiceClassImpl(connectionInfo, timeouts),
      connectionInfo,
      batchSupport = None
    )
  }

  private[service] def buildApiKeyHeaders(apiKey: String): Seq[(String, String)] =
    Seq(("x-api-key", apiKey), ("anthropic-version", apiVersion))

  private[service] def buildAuthTokenHeaders(
    authToken: String,
    withOAuthBeta: Boolean
  ): Seq[(String, String)] = {
    val betaHeader =
      if (withOAuthBeta) Seq(("anthropic-beta", AnthropicOAuthConsts.oauthBetaValue)) else Nil

    Seq(("Authorization", s"Bearer $authToken"), ("anthropic-version", apiVersion)) ++
      betaHeader
  }

  private[service] def defaultAuthToken: String = {
    def env(key: String): Option[String] = Option(System.getenv(key)).filter(_.trim.nonEmpty)

    env(EnvKeys.anthropicAuthToken)
      .orElse(env(EnvKeys.claudeCodeOAuthTokenAlternative))
      .orElse(env(EnvKeys.claudeCodeOAuthToken))
      .getOrElse(
        throw new IllegalArgumentException(
          s"None of ${EnvKeys.anthropicAuthToken}, ${EnvKeys.claudeCodeOAuthTokenAlternative}, or " +
            s"${EnvKeys.claudeCodeOAuthToken} env. variable is set."
        )
      )
  }

  /**
   * Create a new instance of the [[AnthropicService]]
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def apply(
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService = {
    // Only auth headers ride on every request. Feature beta headers are sent per-operation
    // (see Anthropic.messageBetaHeaders / fileBetaHeaders / skillHeaders / managedAgentsHeaders),
    // because the managed-agents control-plane endpoints reject any beta value other than their
    // own and would 400 on always-on message betas. The OAuth auth-mode beta
    // (oauth-2025-04-20, see [[forAuthToken]]) is the one exception - it is an auth-mode flag
    // (not a feature beta) that must accompany every OAuth-authenticated request, and is
    // accepted by the managed-agents control plane alongside managed-agents-2026-04-01 (two
    // anthropic-beta header instances = HTTP list semantics, same wire meaning as the SDKs'
    // comma-joined value).
    val authHeaders = buildApiKeyHeaders(apiKey)

    new AnthropicServiceClassImpl(
      defaultCoreUrl,
      () => WsRequestContext(authHeaders = authHeaders),
      timeouts,
      withPdf,
      withCache
    )
  }

  /**
   * Creates the service on a CALLER-SUPPLIED, SITE-STATELESS streaming engine - e.g. one
   * shared with other providers via `StreamedEngineRegistry.outputStreamed()` - so several
   * providers can share one connection pool and actor system. The site binding (base URL, auth
   * headers, error recovery, logging label) is built here from `apiKey` and held by the
   * service, threaded into every engine call. Closing such a service does NOT close the shared
   * engine - close the engine once, when done with all services using it.
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   */
  def withEngine(
    engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService = {
    val authHeaders = buildApiKeyHeaders(apiKey)

    new AnthropicServiceEngineImpl(
      engine,
      SiteBinding(
        defaultCoreUrl,
        WsRequestContext(authHeaders = authHeaders),
        recoverErrors = Some(recoverErrors),
        label = Some("anthropic")
      ),
      withPdf,
      withCache
    )
  }

  /**
   * Bearer/OAuth alternative to [[apply]] for the direct Anthropic API.
   *
   * Token sources (in order): the `authToken` argument; if not supplied, the
   * `ANTHROPIC_AUTH_TOKEN` env. variable; if that is unset, `CLAUDE_CODE_OAUTH_TOKEN`.
   * `ANTHROPIC_AUTH_TOKEN` is typically a platform OAuth token minted via `ant auth login` and
   * retrieved with `ant auth print-credentials --access-token`, or a gateway-issued bearer
   * token (pass `withOAuthBeta = false` for gateways, since the `oauth-2025-04-20` beta is
   * specific to Anthropic's own OAuth flow). `CLAUDE_CODE_OAUTH_TOKEN` (from `claude
   * setup-token`) is a best-effort fallback only - these tokens are scoped to the Claude Code
   * backend and are documented as rejected by the public API (expect a 401). Subscription
   * usage for agents is sanctioned only through the Claude Agent SDK/CLI harness (the "Agent
   * SDK credit" introduced 2026-06-15), not through this REST client.
   *
   * This auth mode is mutually exclusive with the x-api-key mode of [[apply]] by construction
   * \- exactly one of `x-api-key` / `Authorization: Bearer` rides on the request.
   *
   * @param authToken
   *   The bearer/OAuth token to use for authentication (if not specified, resolved from env.
   *   variables as described above)
   * @param withOAuthBeta
   *   Whether to send the `oauth-2025-04-20` auth-mode beta header (default true). Set to
   *   false for gateway-issued bearer tokens that aren't Anthropic's own OAuth flow.
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def forAuthToken(
    authToken: String = defaultAuthToken,
    withOAuthBeta: Boolean = true,
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService = {
    val authHeaders = buildAuthTokenHeaders(authToken, withOAuthBeta)

    new AnthropicServiceClassImpl(
      defaultCoreUrl,
      () => WsRequestContext(authHeaders = authHeaders),
      timeouts,
      withPdf,
      withCache
    )
  }

  /**
   * Bearer/OAuth alternative to [[apply]] backed by an [[AnthropicTokenProvider]] that is
   * consulted on '''every''' request - not just once at construction time - so it can hand out
   * freshly refreshed tokens as they expire. Use this (or [[forOAuthProfile]], which is built
   * on top of it) instead of [[forAuthToken]] whenever the token has a finite lifetime.
   *
   * @param provider
   *   Supplies the bearer token (and any extra headers) for each individual request.
   * @param withOAuthBeta
   *   Whether to send the `oauth-2025-04-20` auth-mode beta header (default true).
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def forAuthTokenProvider(
    provider: AnthropicTokenProvider,
    withOAuthBeta: Boolean = true,
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService =
    new AnthropicServiceClassImpl(
      defaultCoreUrl,
      () =>
        WsRequestContext(
          authHeaders = buildAuthTokenHeaders(provider.accessToken(), withOAuthBeta) ++
            provider.extraHeaders
        ),
      timeouts,
      withPdf,
      withCache
    )

  /**
   * [[AnthropicService]] authenticated via an `ant auth login` OAuth profile, with automatic
   * token refresh on every request - mirroring the credential-chain behaviour of the official
   * Anthropic SDKs. Profiles are read from `~/.config/anthropic/` (or the directory pointed to
   * by `ANTHROPIC_CONFIG_DIR`), with the profile itself resolved via `ANTHROPIC_PROFILE`, the
   * `active_config` file, or the `profile` argument.
   *
   * @param profile
   *   Named profile to use (if not specified, resolved via ANTHROPIC_PROFILE / active_config).
   * @param configDir
   *   Directory holding the `ant auth login` config (if not specified, resolved via
   *   ANTHROPIC_CONFIG_DIR, defaulting to `~/.config/anthropic/`).
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def forOAuthProfile(
    profile: Option[String] = None,
    configDir: Option[Path] = None,
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService =
    forAuthTokenProvider(
      new AnthropicOAuthProfileTokenProvider(configDir, profile),
      withOAuthBeta = true,
      timeouts,
      withPdf,
      withCache
    )

  /**
   * Escape hatch mirroring `OpenAIServiceFactory.customInstance` - builds an
   * [[AnthropicService]] from a fully custom base URL and [[WsRequestContext]] (auth headers,
   * extra params), for cases not covered by the other factory methods (e.g. gateways with
   * bespoke auth schemes).
   *
   * @param coreUrl
   *   Base URL of the Anthropic-compatible API (default: the direct Anthropic API URL).
   * @param requestContext
   *   Fully custom request context (auth headers, extra params), held constant across
   *   requests.
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def customInstance(
    coreUrl: String = defaultCoreUrl,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService =
    new AnthropicServiceClassImpl(coreUrl, () => requestContext, timeouts, withPdf, withCache)

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
    implicit ec: ExecutionContext
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
    implicit ec: ExecutionContext
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
   *
   * Note: the returned service has no Bedrock batch support wired in - batch calls fail with a
   * message pointing to [[bedrockAsOpenAIWithBatchSupport]].
   */
  def bedrockAsOpenAIWithSessionToken(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    durationSeconds: Int = 3600,
    inferenceProfilePrefix: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService = {
    val sts = BedrockStsClient.getSessionToken(accessKey, secretKey, durationSeconds)
    val connectionInfo = BedrockConnectionSettings(
      accessKey = sts.accessKeyId,
      secretKey = sts.secretAccessKey,
      region = region,
      inferenceProfilePrefix = inferenceProfilePrefix,
      sessionToken = Some(sts.sessionToken)
    )

    new OpenAIAnthropicBedrockChatCompletionService(
      new AnthropicBedrockServiceClassImpl(connectionInfo, timeouts),
      connectionInfo,
      batchSupport = None
    )
  }

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
    implicit ec: ExecutionContext
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

  /**
   * [[AnthropicService]] backed by the Amazon Bedrock `bedrock-mantle` endpoint, which serves
   * Claude models from the Anthropic-native Messages API at the provider-scoped `anthropic/v1`
   * base path: `https://bedrock-mantle.<region>.api.aws/anthropic/v1/messages` (Claude models
   * reject mantle's OpenAI-compatible `/v1/chat/completions` and `/v1/responses` paths).
   *
   * Unlike [[forBedrock]] / [[forBedrockWithBearerToken]] (the `bedrock-runtime` host with
   * dated model ids such as `anthropic.claude-haiku-4-5-20251001-v1:0` and optional
   * cross-region inference profile prefixes), `bedrock-mantle` uses the short-form model ids
   * the endpoint advertises via `/v1/models`, e.g.
   * [[io.cequence.openaiscala.domain.NonOpenAIModelId.bedrock_claude_haiku_4_5]]
   * (`anthropic.claude-haiku-4-5`). The standard `anthropic-version` header is accepted, so
   * the native request shape applies unmodified - no SigV4 signing, no `anthropic_version`
   * body field needed.
   *
   * Note that Claude model availability on mantle is region-gated (verified July 2026: served
   * in `eu-north-1`, absent in `us-east-2`) - check the endpoint's model list for your region.
   *
   * @param apiKey
   *   Bedrock API key (sent as a bearer token). Defaults to the `AWS_BEARER_TOKEN_BEDROCK` env
   *   var.
   * @param region
   *   AWS region, e.g. "eu-north-1". Defaults to the `AWS_BEDROCK_REGION` env var.
   */
  def forBedrockMantle(
    apiKey: String = getEnvValue(EnvKeys.bedrockBearerToken),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService =
    customInstance(
      bedrockMantleCoreUrl(region),
      WsRequestContext(authHeaders = buildAuthTokenHeaders(apiKey, withOAuthBeta = false)),
      timeouts,
      withPdf,
      withCache
    )

  /**
   * Amazon Bedrock `bedrock-mantle` variant backed by a caller-owned shared engine - see
   * [[withEngine]] for the engine-sharing semantics (closing this service does NOT close the
   * engine) and [[forBedrockMantle]] for the mantle endpoint semantics. Mirrors
   * `OpenAIServiceFactory.forBedrockMantleWithEngine`, so one engine can serve both the
   * OpenAI-provider mantle models and Claude.
   */
  def forBedrockMantleWithEngine(
    engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    apiKey: String,
    region: String,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext
  ): AnthropicService =
    new AnthropicServiceEngineImpl(
      engine,
      SiteBinding(
        bedrockMantleCoreUrl(region),
        WsRequestContext(authHeaders = buildAuthTokenHeaders(apiKey, withOAuthBeta = false)),
        recoverErrors = Some(recoverErrors),
        label = Some("anthropic")
      ),
      withPdf,
      withCache
    )

  private class AnthropicServiceClassImpl(
    coreUrl: String,
    requestContextFun: () => WsRequestContext,
    explTimeouts: Option[Timeouts] = None,
    pdfEnabled: Boolean = false,
    cacheEnabled: Boolean = false
  )(
    implicit val ec: ExecutionContext
  ) extends AnthropicServiceImpl {

    override protected def withPdf: Boolean = pdfEnabled
    override protected def withCache: Boolean = cacheEnabled

    // classpath-discovered engine with output streaming (SSE) support, owned (and closed) by
    // this service
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
      StreamedEngineRegistry.outputStreamed(
        TransportSettings(timeouts = explTimeouts.getOrElse(Timeouts()))
      )

    override protected val site: SiteBinding =
      SiteBinding(
        coreUrl,
        requestContextFun = Some(requestContextFun),
        recoverErrors = Some(recoverErrors),
        label = Some("anthropic")
      )
  }

  private class AnthropicServiceEngineImpl(
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    override protected val site: SiteBinding,
    pdfEnabled: Boolean,
    cacheEnabled: Boolean
  )(
    implicit val ec: ExecutionContext
  ) extends AnthropicServiceImpl {

    override protected def withPdf: Boolean = pdfEnabled
    override protected def withCache: Boolean = cacheEnabled

    // the engine is shared/caller-supplied - closed by its creator, not by this service
    override protected def ownsEngine: Boolean = false
  }

  private class AnthropicBedrockServiceClassImpl(
    override val connectionInfo: BedrockConnectionSettings,
    explTimeouts: Option[Timeouts] = None,
    externalEngine: Option[WSClientEngine with WSClientOutputStreamExtraAkka] = None
  )(
    implicit val ec: ExecutionContext
  ) extends AnthropicBedrockServiceImpl {

    // a caller-supplied shared streaming engine, or a privately-owned discovered engine
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
      externalEngine.getOrElse(
        StreamedEngineRegistry.outputStreamed(
          TransportSettings(timeouts = explTimeouts.getOrElse(Timeouts()))
        )
      )

    override protected def ownsEngine: Boolean = externalEngine.isEmpty

    override protected val site: SiteBinding =
      SiteBinding(
        bedrockCoreUrl(connectionInfo.region),
        recoverErrors = Some(recoverErrors),
        label = Some("anthropic-bedrock")
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
        // the discovery path normalizes transport failures to the Cequence taxonomy before
        // this recovery is applied; the raw types are kept as a safety net
        case e @ (_: CequenceWSTimeoutException | _: TimeoutException) =>
          throw new AnthropicScalaClientTimeoutException(
            s"${serviceEndPointName} timed out: ${e.getMessage}."
          )
        case e @ (_: CequenceWSUnknownHostException | _: UnknownHostException) =>
          throw new AnthropicScalaClientUnknownHostException(
            s"${serviceEndPointName} cannot resolve a host name: ${e.getMessage}."
          )
      }
  }
}
