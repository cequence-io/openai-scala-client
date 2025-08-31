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
 *   - web_search_call.action.sources: Include the sources of the web search tool call.
 *   - code_interpreter_call.outputs: Includes the outputs of python code execution in code
 *     interpreter tool call items.
 *   - computer_call_output.output.image_url: Include image urls from the computer call output.
 *   - file_search_call.results: Include the search results of the file search tool call.
 *   - message.input_image.image_url: Include image urls from the input message.
 *   - message.output_text.logprobs: Include logprobs with assistant messages.
 *   - reasoning.encrypted_content: Includes an encrypted version of reasoning tokens in
 *     reasoning item outputs. This enables reasoning items to be used in multi-turn
 *     conversations when using the Responses API statelessly (like when the store parameter is
 *     set to false, or when an organization is enrolled in the zero data retention program).
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
 * @param prompt
 *   Reference to a prompt template and its variables.
 * @param promptCacheKey
 *   Used by OpenAI to cache responses for similar requests to optimize your cache hit rates.
 *   Replaces the user field.
 * @param background
 *   Whether to run the model response in the background. Optional, defaults to false.
 * @param maxToolCalls
 *   The maximum number of total calls to built-in tools that can be processed in a response.
 *   This maximum number applies across all built-in tool calls, not per individual tool. Any
 *   further attempts to call a tool by the model will be ignored. Optional.
 * @param safetyIdentifier
 *   A stable identifier used to help detect users of your application that may be violating
 *   OpenAI's usage policies. The IDs should be a string that uniquely identifies each user. We
 *   recommend hashing their username or email address, in order to avoid sending us any
 *   identifying information Optional.
 * @param serviceTier
 *   Specifies the processing type used for serving the request.
 *   - If set to 'auto', then the request will be processed with the service tier configured in
 *     the Project settings. Unless otherwise configured, the Project will use 'default'.
 *   - If set to 'default', then the request will be processed with the standard pricing and
 *     performance for the selected model.
 *   - If set to 'flex' or 'priority', then the request will be processed with the
 *     corresponding service tier.
 *   - When not set, the default behavior is 'auto'.
 * @param streamOptions
 *   Options for streaming responses.
 * @param topLogprobs
 *   An integer between 0 and 20 specifying the number of most likely tokens to return at each
 *   token position, each with an associated log probability. Optional
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
  user: Option[String] = None,
  prompt: Option[Prompt] = None,
  promptCacheKey: Option[String] = None,
  background: Option[Boolean] = None,
  maxToolCalls: Option[Int] = None,
  safetyIdentifier: Option[String] = None,
  serviceTier: Option[String] = None,
  streamOptions: Option[StreamOptions] = None,
  topLogprobs: Option[Int] = None
)

object CreateModelResponseSettings {

  def toAuxPart1(x: CreateModelResponseSettings) =
    CreateModelResponseSettingsAuxPart1(
      model = x.model,
      include = x.include,
      instructions = x.instructions,
      maxOutputTokens = x.maxOutputTokens,
      metadata = x.metadata,
      parallelToolCalls = x.parallelToolCalls,
      previousResponseId = x.previousResponseId,
      reasoning = x.reasoning,
      store = x.store,
      stream = x.stream,
      temperature = x.temperature,
      text = x.text
    )

  def toAuxPart2(x: CreateModelResponseSettings) =
    CreateModelResponseSettingsAuxPart2(
      toolChoice = x.toolChoice,
      tools = x.tools,
      topP = x.topP,
      truncation = x.truncation,
      user = x.user,
      prompt = x.prompt,
      promptCacheKey = x.promptCacheKey,
      background = x.background,
      maxToolCalls = x.maxToolCalls,
      safetyIdentifier = x.safetyIdentifier,
      serviceTier = x.serviceTier,
      streamOptions = x.streamOptions,
      topLogprobs = x.topLogprobs
    )

  private def fromParts(
    part1: CreateModelResponseSettingsAuxPart1,
    part2: CreateModelResponseSettingsAuxPart2
  ) =
    CreateModelResponseSettings(
      model = part1.model,
      include = part1.include,
      instructions = part1.instructions,
      maxOutputTokens = part1.maxOutputTokens,
      metadata = part1.metadata,
      parallelToolCalls = part1.parallelToolCalls,
      previousResponseId = part1.previousResponseId,
      reasoning = part1.reasoning,
      store = part1.store,
      stream = part1.stream,
      temperature = part1.temperature,
      text = part1.text,
      toolChoice = part2.toolChoice,
      tools = part2.tools,
      topP = part2.topP,
      truncation = part2.truncation,
      user = part2.user,
      prompt = part2.prompt,
      promptCacheKey = part2.promptCacheKey,
      background = part2.background,
      maxToolCalls = part2.maxToolCalls,
      safetyIdentifier = part2.safetyIdentifier,
      serviceTier = part2.serviceTier,
      streamOptions = part2.streamOptions,
      topLogprobs = part2.topLogprobs
    )
}

final case class CreateModelResponseSettingsAuxPart1(
  model: String,
  include: Seq[String],
  instructions: Option[String],
  maxOutputTokens: Option[Int],
  metadata: Option[Map[String, String]],
  parallelToolCalls: Option[Boolean],
  previousResponseId: Option[String],
  reasoning: Option[ReasoningConfig],
  store: Option[Boolean],
  stream: Option[Boolean],
  temperature: Option[Double],
  text: Option[TextResponseConfig]
)

final case class CreateModelResponseSettingsAuxPart2(
  toolChoice: Option[ToolChoice],
  tools: Seq[Tool],
  topP: Option[Double],
  truncation: Option[TruncationStrategy],
  user: Option[String],
  prompt: Option[Prompt],
  promptCacheKey: Option[String],
  background: Option[Boolean],
  maxToolCalls: Option[Int],
  safetyIdentifier: Option[String],
  serviceTier: Option[String],
  streamOptions: Option[StreamOptions],
  topLogprobs: Option[Int]
)
