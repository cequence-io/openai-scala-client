package io.cequence.openaiscala.domain.settings

import io.cequence.wsclient.domain.EnumValue

case class CreateChatCompletionSettings(
  // ID of the model to use. Currently, only gpt-3.5-turbo and gpt-3.5-turbo-0301 are supported.
  model: String,

  // What sampling temperature to use, between 0 and 2.
  // Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic.
  // We generally recommend altering this or top_p but not both. Defaults to 1.
  temperature: Option[Double] = None,

  // An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass.
  // So 0.1 means only the tokens comprising the top 10% probability mass are considered.
  // We generally recommend altering this or temperature but not both. Defaults to 1.
  top_p: Option[Double] = None,

  // How many chat completion choices to generate for each input message. Defaults to 1.
  n: Option[Int] = None,

  // Up to 4 sequences where the API will stop generating further tokens.
  stop: Seq[String] = Nil, // Option[String or Array]

  // The maximum number of tokens to generate in the chat completion.
  // The total length of input tokens and generated tokens is limited by the model's context length.
  // Defaults to inf.
  // TODO: should be renamed to max_completion_tokens in future :)
  max_tokens: Option[Int] = None,

  // Number between -2.0 and 2.0.
  // Positive values penalize new tokens based on whether they appear in the text so far,
  // increasing the model's likelihood to talk about new topics.
  // Defaults to 0.
  presence_penalty: Option[Double] = None,

  // Number between -2.0 and 2.0.
  // Positive values penalize new tokens based on their existing frequency in the text so far,
  // decreasing the model's likelihood to repeat the same line verbatim.
  // Defaults to 0.
  frequency_penalty: Option[Double] = None,

  // Modify the likelihood of specified tokens appearing in the completion.
  // Accepts a json object that maps tokens (specified by their token ID in the tokenizer) to an associated bias value from -100 to 100.
  // Mathematically, the bias is added to the logits generated by the model prior to sampling.
  // The exact effect will vary per model, but values between -1 and 1 should decrease or increase likelihood of selection;
  // values like -100 or 100 should result in a ban or exclusive selection of the relevant token.
  logit_bias: Map[String, Int] = Map(),

  // Whether to return log probabilities of the output tokens or not.
  // If true, returns the log probabilities of each output token returned in the content of message.
  // This option is currently not available on the gpt-4-vision-preview model.
  // Defaults to false
  logprobs: Option[Boolean] = None,

  // An integer between 0 and 5 specifying the number of most likely tokens to return at each token position, each with an associated log probability.
  // logprobs must be set to true if this parameter is used.
  top_logprobs: Option[Int] = None,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None,

  // The format that the model must output. Must be one of text or json_object.
  // Setting to "json_object" enables JSON mode, which guarantees the message the model generates is valid JSON.
  // Important: when using JSON mode, you must also instruct the model to produce JSON yourself via a system or user message.
  // Without this, the model may generate an unending stream of whitespace until the generation reaches the token limit,
  // resulting in a long-running and seemingly "stuck" request. Also note that the message content may be partially cut off
  // if finish_reason="length", which indicates the generation exceeded max_tokens or the conversation exceeded the max context length.
  // Defaults to text
  response_format_type: Option[ChatCompletionResponseFormatType] = None, // new

  // This feature is in Beta. If specified, our system will make a best effort to sample deterministically,
  // such that repeated requests with the same seed and parameters should return the same result.
  // Determinism is not guaranteed, and you should refer to the system_fingerprint response parameter to monitor changes in the backend.
  seed: Option[Int] = None,

  // json schema to use if response format = json_schema
  jsonSchema: Option[JsonSchemaDef] = None,

  // Whether or not to store the output of this chat completion request for use in our model distillation or evals products.
  store: Option[Boolean] = None,

  // Constrains effort on reasoning for reasoning models
  // Currently supported values are low, medium, and high.
  // Reducing reasoning effort can result in faster responses and fewer tokens used on reasoning in a response.
  // Supported by o1 models only
  reasoning_effort: Option[ReasoningEffort] = None,

  // Specifies the latency tier to use for processing the request. This parameter is relevant for customers subscribed to the scale tier service:
  // If set to 'auto', and the Project is Scale tier enabled, the system will utilize scale tier credits until they are exhausted.
  // If set to 'auto', and the Project is not Scale tier enabled, the request will be processed using the default service tier with a lower uptime SLA and no latency guarentee.
  // If set to 'default', the request will be processed using the default service tier with a lower uptime SLA and no latency guarantee.
  // When not set, the default behavior is 'auto'.
  service_tier: Option[ServiceTier] = None,

  // Whether to enable parallel function calling during tool use.
  parallel_tool_calls: Option[Boolean] = None,

  // Developer-defined tags and values used for filtering completions in the dashboard.
  // The 'metadata' parameter is only allowed when 'store' is enabled.
  metadata: Map[String, String] = Map(),

  //  // Output types that you would like the model to generate for this request. Most models are capable of generating text, which is the default:
  //  // ["text"]
  //  // The gpt-4o-audio-preview model can also be used to generate audio. To request that this model generate both text and audio responses, you can use:
  //  // ["text", "audio"]
  //  // TODO: support this
  //  modalities: Seq[String] = Nil, // enum?
  //
  //  // Configuration for a Predicted Output, which can greatly improve response times when large parts of the model response are known ahead of time.
  //  // This is most common when you are regenerating a file with only minor changes to most of the content.
  //  // TODO: support this
  //  prediction: Option[Any] = None,
  //
  //  // Parameters for audio output. Required when audio output is requested with modalities: ["audio"].
  //  // TODO: support this
  //  audio: Option[Any] = None,

  // ad-hoc parameters, not part of the OpenAI API, e.g. for other providers or experimental features
  extra_params: Map[String, Any] = Map.empty
) {

  def withJsonSchema(jsonSchema: JsonSchemaDef): CreateChatCompletionSettings =
    copy(jsonSchema = Some(jsonSchema))

}

sealed trait ChatCompletionResponseFormatType extends EnumValue

object ChatCompletionResponseFormatType {
  case object text extends ChatCompletionResponseFormatType
  case object json_object extends ChatCompletionResponseFormatType
  case object json_schema extends ChatCompletionResponseFormatType
}

sealed trait ReasoningEffort extends EnumValue

object ReasoningEffort {
  case object low extends ReasoningEffort
  case object medium extends ReasoningEffort
  case object high extends ReasoningEffort
}

sealed trait ServiceTier extends EnumValue

object ServiceTier {
  case object auto extends ServiceTier
  case object default extends ServiceTier
}
