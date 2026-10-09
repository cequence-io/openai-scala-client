package io.cequence.openaiscala.domain.guardrails

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

/**
 * Settings of a guardrail answered by a chat model (`ModelGuardrail`): the checks of a stage
 * are asked in one call, as one yes/no property each of a JSON schema.
 *
 * @param model
 *   the guard model - an LLM (e.g. `gpt-5.4-mini`), or a decision model through
 *   `TypeSafeServiceFactory.asOpenAI` (e.g. `jev-latest`), whose answer to each check is a
 *   calibrated yes/no question
 * @param inputChecks
 *   the questions about a call's user messages (input stage)
 * @param outputChecks
 *   the questions about a reply (output stage)
 * @param policy
 *   extra rules for the guard, e.g. "Questions about other companies' products are
 *   off-topic.", filled into the prompts' `{{policy}}` placeholder
 * @param prompts
 *   the guard's system prompts (templates with `{{policy}}` / `{{checks}}`), one per stage
 * @param failOpen
 *   what happens when the guard cannot answer (an error, or no usable answer after
 *   `parseRetries`): `false` (default) blocks the message as
 *   [[GuardrailVerdict.UnavailableCheck]], `true` passes it (logged)
 * @param name
 *   the guardrail's name in verdicts and logs
 * @param scope
 *   which user messages an input check reads
 * @param parseRetries
 *   how many times an answer that is not a verdict is asked again. Transient errors (timeouts,
 *   429, 5xx) are not retried here - wrap the guard service with `OpenAIServiceAdapters.retry`
 *   for those.
 * @param enforceJsonSchemaMode
 *   send the JSON schema even when `model` is not in the configured
 *   `models-supporting-json-schema` - for an LLM that takes one although the config does not
 *   list it; otherwise such a model gets the schema in the prompt (JSON-object mode), which a
 *   decision model's adapter reads back from there, so it does not need this
 * @param adjustSettings
 *   adjusts the guard call's settings - e.g. `_.copy(reasoning_effort =
 *   Some(ReasoningEffort.none))` to keep a reasoning LLM quick. With a decision model, the
 *   noul threshold (`_.setTypeSafeNoulThreshold(0.7)`) decides only the checks without a
 *   `threshold` of their own: a check with one compares the probability itself, whatever the
 *   noul threshold - set the check's `threshold` instead
 */
final case class ModelGuardrailSettings(
  model: String,
  inputChecks: Seq[GuardrailCheck] = GuardrailCheck.inputDefaults,
  outputChecks: Seq[GuardrailCheck] = GuardrailCheck.outputDefaults,
  policy: Option[String] = None,
  prompts: GuardrailPrompts = GuardrailPrompts(),
  failOpen: Boolean = false,
  name: String = "moderation",
  scope: GuardrailScope = GuardrailScope.NewUserMessages,
  parseRetries: Int = 2,
  enforceJsonSchemaMode: Boolean = false,
  adjustSettings: CreateChatCompletionSettings => CreateChatCompletionSettings =
    identity[CreateChatCompletionSettings]
) {
  require(model.trim.nonEmpty, "The guard model is not set.")
  require(parseRetries >= 0, s"parseRetries must not be negative, got $parseRetries.")

  // a policy needs a place in the prompt of every stage that asks something
  if (policy.exists(_.trim.nonEmpty))
    Seq(
      "input" -> (inputChecks, prompts.input),
      "output" -> (outputChecks, prompts.output)
    ).foreach { case (stage, (checks, prompt)) =>
      require(
        checks.isEmpty || prompt.contains(GuardrailPrompts.PolicyPlaceholder),
        s"The $stage prompt of the guardrail '$name' has no ${GuardrailPrompts.PolicyPlaceholder} " +
          "placeholder, so its policy would be ignored."
      )
    }

  Seq("input" -> inputChecks, "output" -> outputChecks).foreach { case (stage, checks) =>
    val duplicates = checks.groupBy(_.name).collect { case (check, Seq(_, _, _*)) => check }
    require(
      duplicates.isEmpty,
      s"The $stage checks of the guardrail '$name' repeat ${duplicates.mkString(", ")}."
    )
  }
}
