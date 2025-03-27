package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.responsesapi.tools.ToolChoice
import io.cequence.openaiscala.domain.responsesapi.tools.Tool

/**
 * Settings for generating a response from the model.
 *
 * @param model
 *   Required. Model ID used to generate the response, like gpt-4o or o1.
 * @param include
 *   Optional. Specify additional output data to include in the model response. Currently
 *   supported values include file_search_call.results, message.input_image.image_url, etc.
 * @param instructions
 *   Optional. Inserts a system (or developer) message as the first item in the model's
 *   context.
 * @param maxOutputTokens
 *   Optional. An upper bound for the number of tokens that can be generated for a response.
 * @param metadata
 *   Optional. Set of 16 key-value pairs that can be attached to an object for storing
 *   additional information.
 * @param parallelToolCalls
 *   Optional. Defaults to true. Whether to allow the model to run tool calls in parallel.
 * @param previousResponseId
 *   Optional. The unique ID of the previous response to the model. Used for multi-turn
 *   conversations.
 * @param reasoning
 *   Optional. Configuration options for reasoning models (o-series models only).
 * @param store
 *   Optional. Defaults to true. Whether to store the generated model response for later
 *   retrieval via API.
 * @param stream
 *   Optional. Defaults to false. If true, the model response data will be streamed to the
 *   client as it is generated.
 * @param temperature
 *   Optional. Defaults to 1. Sampling temperature between 0 and 2. Higher values make output
 *   more random.
 * @param text
 *   Optional. Configuration options for a text response from the model. Can be plain text or
 *   structured JSON data.
 * @param toolChoice
 *   Optional. How the model should select which tool (or tools) to use when generating a
 *   response.
 * @param tools
 *   Optional. An array of tools the model may call while generating a response.
 * @param topP
 *   Optional. Defaults to 1. Alternative to temperature sampling, where model considers tokens
 *   with top_p probability mass.
 * @param truncation
 *   Optional. Defaults to disabled. The truncation strategy to use for the model response.
 * @param user
 *   Optional. A unique identifier representing your end-user, which can help OpenAI to monitor
 *   and detect abuse.
 */
final case class ResponseSettings(
  model: String,
  include: Seq[String] = Nil,
  instructions: Option[String] = None,
  maxOutputTokens: Option[Int] = None,
  metadata: Option[Map[String, String]] = None,
  parallelToolCalls: Option[Boolean] = None,
  previousResponseId: Option[String] = None,
  reasoning: Option[ReasoningConfig] = None,
  store: Option[Boolean] = None,
  stream: Option[Boolean] = None,
  temperature: Option[Double] = None,
  text: Option[TextResponseConfig] = None,
  toolChoice: Option[ToolChoice] = None,
  tools: Seq[Tool] = Nil,
  topP: Option[Double] = Some(1.0),
  truncation: Option[TruncationStrategy] = None,
  user: Option[String] = None
)
