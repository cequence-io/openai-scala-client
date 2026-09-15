package io.cequence.openaiscala.service

/**
 * Which AWS host serves the OpenAI-compatible Bedrock surface.
 *
 * Each case knows its own base URL, so callers that build a service by hand (a PrivateLink
 * alias, a custom gateway, a hand-rolled engine) can reuse the exact URL the factories use
 * rather than re-typing the host and path.
 */
sealed trait BedrockEndpoint {

  /**
   * The base URL to point a service at.
   *
   * @param isOpenAIModel
   *   the OpenAI provider models (e.g. `openai.gpt-5.6-luna`) are served from the `openai/v1`
   *   base path; everything else (e.g. the gpt-oss family) from the plain `v1` path. Ignored
   *   by [[BedrockEndpoint.Runtime]], whose OpenAI-compatible surface is always `openai/v1`.
   */
  def coreUrl(
    region: String,
    isOpenAIModel: Boolean = false
  ): String
}

object BedrockEndpoint {

  /** `bedrock-mantle` serves most models from the standard `v1` base path. */
  val defaultBasePath = "v1"

  /** The OpenAI provider models are the exception - they live under `openai/v1`. */
  val openAIBasePath = "openai/v1"

  /**
   * `https://bedrock-mantle.$region.api.aws/{v1|openai/v1}/` - the newer OpenAI-compatible
   * host. Serves the dated OpenAI snapshots that the runtime host rejects, but only bare
   * provider model ids: it has no cross-region inference profiles.
   */
  case object Mantle extends BedrockEndpoint {
    override def coreUrl(
      region: String,
      isOpenAIModel: Boolean = false
    ): String = {
      val basePath = if (isOpenAIModel) openAIBasePath else defaultBasePath
      s"https://bedrock-mantle.$region.api.aws/$basePath/"
    }
  }

  /**
   * `https://bedrock-runtime.$region.amazonaws.com/openai/v1/` - the classic Bedrock runtime,
   * which serves the same OpenAI-compatible wire format and additionally accepts the
   * cross-region inference-profile model ids (`us.openai.*`, `global.openai.*`).
   */
  case object Runtime extends BedrockEndpoint {
    override def coreUrl(
      region: String,
      isOpenAIModel: Boolean = false
    ): String =
      s"https://bedrock-runtime.$region.amazonaws.com/$openAIBasePath/"
  }
}
