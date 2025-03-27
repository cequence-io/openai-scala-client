package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.responsesapi.tools.ToolChoice
import io.cequence.openaiscala.domain.responsesapi.tools.Tool

/**
 * Response from creating a model.
 *
 * @param model
 *   Model ID used to generate the response, like gpt-4o or o1.
 * @param include
 *   Specify additional output data to include in the model response. Currently supported
 *   values are:
 *   - file_search_call.results: Include the search results of the file search tool call.
 *   - message.input_image.image_url: Include image urls from the input message.
 *   - computer_call_output.output.image_url: Include image urls from the computer call output.
 * @param instructions
 *   Inserts a system (or developer) message as the first item in the model's context.
 * @param maxOutputTokens
 *   An upper bound for the number of tokens that can be generated for a response, including
 *   visible output tokens and reasoning tokens.
 * @param metadata
 *   Set of 16 key-value pairs that can be attached to an object.
 * @param parallelToolCalls
 *   Whether to allow the model to run tool calls in parallel.
 * @param previousResponseId
 *   The unique ID of the previous response to the model.
 * @param reasoning
 *   Configuration options for reasoning models (o-series models only).
 * @param store
 *   Whether to store the generated model response for later retrieval via API.
 * @param stream
 *   If set to true, the model response data will be streamed to the client as it is generated.
 * @param temperature
 *   What sampling temperature to use, between 0 and 2.
 * @param text
 *   Configuration options for a text response from the model.
 * @param format
 *   An object specifying the format that the model must output.
 * @param toolChoice
 *   How the model should select which tool (or tools) to use when generating a response.
 * @param tools
 *   An array of tools the model may call while generating a response.
 * @param topP
 *   An alternative to sampling with temperature, called nucleus sampling.
 * @param truncation
 *   The truncation strategy to use for the model response. auto: If the context of this
 *   response and previous ones exceeds the model's context window size, the model will
 *   truncate the response to fit the context window by dropping input items in the middle of
 *   the conversation. disabled (default): If a model response will exceed the context window
 *   size for a model, the request will fail with a 400 error.
 * @param user
 *   A unique identifier representing your end-user, which can help OpenAI to monitor and
 *   detect abuse. Learn more.
 */
final case class CreateModelResponseSettings(
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
  topP: Option[Double] = None,
  truncation: Option[TruncationStrategy] = None,
  user: Option[String] = None
)
