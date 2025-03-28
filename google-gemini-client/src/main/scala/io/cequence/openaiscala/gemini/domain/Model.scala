package io.cequence.openaiscala.gemini.domain

/**
 * @param name
 *   Required. The resource name of the Model. Refer to Model variants for all allowed values.
 *   Format: models/{model} with a {model} naming convention of: "{baseModelId}-{version}"
 *   Examples: models/gemini-1.5-flash-001
 * @param baseModelId
 *   Required. The name of the base model, pass this to the generation request. Examples:
 *   gemini-1.5-flash
 * @param version
 *   Required. The version number of the model. This represents the major version (1.0 or 1.5)
 * @param displayName
 *   The human-readable name of the model. E.g. "Gemini 1.5 Flash". The name can be up to 128
 *   characters long and can consist of any UTF-8 characters.
 * @param description
 *   A short description of the model.
 * @param inputTokenLimit
 *   Maximum number of input tokens allowed for this model.
 * @param outputTokenLimit
 *   Maximum number of output tokens available for this model.
 * @param supportedGenerationMethods
 *   The model's supported generation methods. The corresponding API method names are defined
 *   as Pascal case strings, such as generateMessage and generateContent.
 * @param temperature
 *   Controls the randomness of the output. Values can range over [0.0,maxTemperature],
 *   inclusive. A higher value will produce responses that are more varied, while a value
 *   closer to 0.0 will typically result in less surprising responses from the model. This
 *   value specifies default to be used by the backend while making the call to the model.
 * @param maxTemperature
 *   The maximum temperature this model can use.
 * @param topP
 *   For Nucleus sampling. Nucleus sampling considers the smallest set of tokens whose
 *   probability sum is at least topP. This value specifies default to be used by the backend
 *   while making the call to the model.
 * @param topK
 *   For Top-k sampling. Top-k sampling considers the set of topK most probable tokens. This
 *   value specifies default to be used by the backend while making the call to the model. If
 *   empty, indicates the model doesn't use top-k sampling, and topK isn't allowed as a
 *   generation parameter.
 */
case class Model(
  name: String,
  baseModelId: Option[String],
  version: String,
  displayName: String,
  description: Option[String],
  inputTokenLimit: Int,
  outputTokenLimit: Int,
  supportedGenerationMethods: Seq[String] = Nil,
  temperature: Option[Double],
  maxTemperature: Option[Double],
  topP: Option[Double],
  topK: Option[Int]
)
