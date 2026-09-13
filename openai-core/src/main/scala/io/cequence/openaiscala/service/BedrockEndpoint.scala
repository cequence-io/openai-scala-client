package io.cequence.openaiscala.service

/** Which AWS host serves the OpenAI-compatible Bedrock surface. */
sealed trait BedrockEndpoint

object BedrockEndpoint {

  /**
   * `https://bedrock-mantle.$region.api.aws/{v1|openai/v1}/` - the host the bearer-token path
   * uses.
   */
  case object Mantle extends BedrockEndpoint

  /**
   * `https://bedrock-runtime.$region.amazonaws.com/openai/v1/` - the classic Bedrock runtime,
   * which serves the same OpenAI-compatible wire format and additionally accepts the
   * cross-region inference-profile model ids (`us.openai.*`, `global.openai.*`).
   */
  case object Runtime extends BedrockEndpoint
}
