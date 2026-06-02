package io.cequence.openaiscala.service

import akka.stream.Materializer
import com.typesafe.config.Config
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.ConfigImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

trait OpenAIServiceFactoryHelper[F] extends OpenAIServiceConsts with HasOpenAIConfig {

  def apply(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val orgIdHeader = orgId.map(("OpenAI-Organization", _))
    val authHeaders = orgIdHeader ++: Seq(
      ("Authorization", s"Bearer $apiKey"),
      ("OpenAI-Beta", "assistants=v2")
    )

    customInstance(defaultCoreUrl, WsRequestContext(timeouts, authHeaders, Nil))
  }

  def apply(
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F =
    apply(clientConfig)

  def apply(
    config: Config
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
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
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val authHeaders = Seq(("api-key", apiKey))
    forAzureAux(resourceName, deploymentId, apiVersion, authHeaders, timeouts)
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
    implicit ec: ExecutionContext,
    materializer: Materializer
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
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val coreUrl =
      s"https://${resourceName}.openai.azure.com/openai/deployments/${deploymentId}/"

    val extraParams = Seq("api-version" -> apiVersion)

    customInstance(
      coreUrl,
      WsRequestContext(
        timeouts,
        authHeaders,
        extraParams
      )
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
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    val basePath =
      if (isOpenAIModel) openAIBedrockMantleBasePath else defaultBedrockMantleBasePath
    val authHeaders = Seq(("Authorization", s"Bearer $apiKey"))
    customInstance(
      bedrockMantleCoreUrl(region, basePath),
      WsRequestContext(timeouts, authHeaders, Nil)
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
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F
}

trait RawWsServiceFactory[F] {

  def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F
}
