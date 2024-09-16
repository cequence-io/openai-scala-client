package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.Run.TruncationStrategy
import io.cequence.openaiscala.domain.response.ResponseFormat

/**
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
 *   deterministic.
 * @param topP
 *   An alternative to sampling with temperature, called nucleus sampling, where the model
 *   considers the results of the tokens with top_p probability mass. So 0.1 means only the
 *   tokens comprising the top 10% probability mass are considered.
 *
 * We generally recommend altering this or temperature but not both.
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
 * @param truncationStrategy
 *   Controls for how a thread will be truncated prior to the run. Use this to control the
 *   intial context window of the run.
 * @param parallelToolCalls
 *   Whether to enable parallel function calling during tool use.
 * @param responseFormat
 *   Specifies the format that the model must output. Compatible with GPT-4o, GPT-4 Turbo, and
 *   all GPT-3.5 Turbo models since gpt-3.5-turbo-1106.
 *
 * Setting to { "type": "json_object" } enables JSON mode, which guarantees the message the
 * model generates is valid JSON.
 *
 * Important: when using JSON mode, you must also instruct the model to produce JSON yourself
 * via a system or user message. Without this, the model may generate an unending stream of
 * whitespace until the generation reaches the token limit, resulting in a long-running and
 * seemingly "stuck" request. Also note that the message content may be partially cut off if
 * finish_reason="length", which indicates the generation exceeded max_tokens or the
 * conversation exceeded the max context length.
 */
final case class CreateThreadAndRunSettings(
  model: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  temperature: Option[Double] = None,
  topP: Option[Double] = None,
  maxPromptTokens: Option[Int] = None,
  maxCompletionTokens: Option[Int] = None,
  truncationStrategy: TruncationStrategy = TruncationStrategy(lastMessages = None),
  parallelToolCalls: Boolean = true,
  responseFormat: Option[ResponseFormat] = None
)
