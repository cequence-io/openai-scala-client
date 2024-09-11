package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.Run.TruncationStrategy
import io.cequence.openaiscala.domain.response.ResponseFormat

/**
 * Settings for creating a new run.
 *
 * @param model
 *   The ID of the Model to be used to execute this run. If a value is provided here, it will
 *   override the model associated with the assistant. If not, the model associated with the
 *   assistant will be used.
 * @param metadata
 *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
 *   storing additional information about the object in a structured format. Keys can be a
 *   maximum of 64 characters long and values can be a maxium of 512 characters long.
 * @param temperature
 *   What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the
 *   output more random, while lower values like 0.2 will make it more focused and
 *   deterministic. Defaults to 1
 * @param topP
 *   An alternative to sampling with temperature, called nucleus sampling, where the model
 *   considers the results of the tokens with top_p probability mass. So 0.1 means only the
 *   tokens comprising the top 10% probability mass are considered. We generally recommend
 *   altering this or temperature but not both. Defaults to 1
 * @param maxPromptTokens
 *   The maximum number of prompt tokens that may be used over the course of the run. The run
 *   will make a best effort to use only the number of prompt tokens specified, across multiple
 *   turns of the run. If the run exceeds the number of prompt tokens specified, the run will
 *   end with status incomplete. See incomplete_details for more info.
 * @param maxCompletionTokens
 *   The maximum number of completion tokens that may be used over the course of the run. The
 *   run will make a best effort to use only the number of completion tokens specified, across
 *   multiple turns of the run. If the run exceeds the number of completion tokens specified,
 *   the run will end with status incomplete. See incomplete_details for more info.
 * @param responseFormat
 *   The response format to use for the run.
 */
case class CreateRunSettings(
  model: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  temperature: Option[Double] = None,
  topP: Option[Double] = None,
  maxPromptTokens: Option[Int] = None,
  maxCompletionTokens: Option[Int] = None,
  truncationStrategy: Option[TruncationStrategy] = None,
  parallelToolCalls: Option[Boolean] = None,
  responseFormat: Option[ResponseFormat] = None
  // TODO: truncation_strategy, parallel_tool_calls, metadata
)
