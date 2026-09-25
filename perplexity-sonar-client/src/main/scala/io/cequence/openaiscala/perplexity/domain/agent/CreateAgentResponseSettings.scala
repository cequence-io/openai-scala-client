package io.cequence.openaiscala.perplexity.domain.agent

import io.cequence.openaiscala.domain.settings.{JsonSchemaDef, ReasoningEffort}

/**
 * Settings of an Agent API request (`POST /v1/agent`). Pick the run either with a `preset`
 * (see [[AgentPreset]]), a `model` (`provider/model`, e.g. `openai/gpt-5.4-mini`), a `models`
 * fallback chain, or a saved `profile`; the other fields override what the preset brings.
 *
 * @param maxOutputTokens
 *   the spec calls it required for a bare `model`, but the API accepts its absence (live
 *   2026-09-25)
 * @param maxSteps
 *   upper bound of the research loop; overrides the preset's value
 * @param previousResponseId
 *   continues from a stored earlier response instead of replaying it in the input
 * @param reasoningEffort
 *   `none`, `minimal`, `low`, `medium`, `high`, `xhigh` or `max` (the `fast` preset runs with
 *   `none`; which values a model accepts depends on the model)
 * @param responseFormat
 *   structured output - the reply text is JSON matching the schema
 * @param languagePreference
 *   ISO 639-1 code of the answer language
 * @param store
 *   `false` hides the response from later retrieve calls
 * @param promptCacheKey
 *   groups requests for prompt caching (the presets use their own name)
 * @param serviceTier
 *   e.g. `priority` (the `fast` preset's tier) - see the models page for each model's tiers
 * @param background
 *   run asynchronously: without streaming the call returns at once with status `queued`; poll
 *   with `retrieveAgentResponse`
 * @param extraParams
 *   anything else, added to the body as is
 */
final case class CreateAgentResponseSettings(
  preset: Option[String] = None,
  model: Option[String] = None,
  models: Seq[String] = Nil,
  profile: Option[AgentProfileReference] = None,
  instructions: Option[String] = None,
  tools: Seq[AgentTool] = Nil,
  skills: Seq[AgentSkill] = Nil,
  maxOutputTokens: Option[Int] = None,
  maxSteps: Option[Int] = None,
  previousResponseId: Option[String] = None,
  reasoningEffort: Option[ReasoningEffort] = None,
  responseFormat: Option[JsonSchemaDef] = None,
  languagePreference: Option[String] = None,
  temperature: Option[Double] = None,
  topP: Option[Double] = None,
  promptCacheKey: Option[String] = None,
  serviceTier: Option[String] = None,
  store: Option[Boolean] = None,
  background: Option[Boolean] = None,
  extraParams: Map[String, Any] = Map.empty
)

/**
 * The Agent API presets (live list: https://docs.perplexity.ai/docs/agent-api/presets). Their
 * Sonar equivalents: `sonar` -> [[fast]], `sonar-pro` -> [[low]], `sonar-reasoning-pro` ->
 * [[medium]], `sonar-deep-research` -> [[high]].
 */
object AgentPreset {
  val fast = "fast"
  val low = "low"
  val medium = "medium"
  val high = "high"
  val xhigh = "xhigh"
  // wide-and-deep research collections, meant for background mode
  val wide_research = "wide-research"
}
