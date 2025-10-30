package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.responsesapi.tools.ToolChoice
import io.cequence.openaiscala.domain.responsesapi.tools.Tool

/**
 * Settings for getting input tokens count.
 *
 * @param conversation
 *   The conversation that this response belongs to. Items from this conversation are prepended
 *   to input_items for this response request. Input items and output items from this response
 *   are automatically added to this conversation after this response completes.
 * @param instructions
 *   A system (or developer) message inserted into the model's context. When used along with
 *   previous_response_id, the instructions from a previous response will not be carried over
 *   to the next response. This makes it simple to swap out system (or developer) messages in
 *   new responses.
 * @param model
 *   Model ID used to generate the response, like gpt-4o or o3. OpenAI offers a wide range of
 *   models with different capabilities, performance characteristics, and price points.
 * @param parallelToolCalls
 *   Whether to allow the model to run tool calls in parallel.
 * @param previousResponseId
 *   The unique ID of the previous response to the model. Use this to create multi-turn
 *   conversations. Cannot be used in conjunction with conversation.
 * @param reasoning
 *   Configuration options for reasoning models (gpt-5 and o-series models only).
 * @param text
 *   Configuration options for a text response from the model. Can be plain text or structured
 *   JSON data.
 * @param toolChoice
 *   How the model should select which tool (or tools) to use when generating a response. See
 *   the tools parameter to see how to specify which tools the model can call.
 * @param tools
 *   An array of tools the model may call while generating a response. You can specify which
 *   tool to use by setting the tool_choice parameter.
 * @param truncation
 *   The truncation strategy to use for the model response.
 *   - auto: If the input to this Response exceeds the model's context window size, the model
 *     will truncate the response to fit the context window by dropping items from the
 *     beginning of the conversation.
 *   - disabled (default): If the input size will exceed the context window size for a model,
 *     the request will fail with a 400 error.
 */
final case class GetInputTokensCountSettings(
  conversation: Option[Conversation] = None,
  instructions: Option[String] = None,
  model: Option[String] = None,
  parallelToolCalls: Option[Boolean] = None,
  previousResponseId: Option[String] = None,
  reasoning: Option[ReasoningConfig] = None,
  text: Option[TextResponseConfig] = None,
  toolChoice: Option[ToolChoice] = None,
  tools: Seq[Tool] = Nil,
  truncation: Option[TruncationStrategy] = None
)
