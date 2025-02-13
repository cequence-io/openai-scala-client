package io.cequence.openaiscala.gemini.domain.settings

import io.cequence.openaiscala.gemini.domain.{Content, HarmBlockThreshold, HarmCategory, Tool}
import io.cequence.wsclient.domain.EnumValue

/**
 * The request body contains data with the following structure:
 *
 * @param model
 *   Required. The model to use for generating content.
 * @param tools
 *   Optional. A list of Tools the Model may use to generate the next response. A Tool is a
 *   piece of code that enables the system to interact with external systems to perform an
 *   action, or set of actions, outside of knowledge and scope of the Model. Supported Tools
 *   are Function and codeExecution. Refer to the Function calling and the Code execution
 *   guides to learn more.
 * @param toolConfig
 *   Optional. Tool configuration for any Tool specified in the request. Refer to the Function
 *   calling guide for a usage example.
 * @param safetySettings
 *   Optional. A list of unique SafetySetting instances for blocking unsafe content. This will
 *   be enforced on the GenerateContentRequest. Refer to the guide for detailed information on
 *   available safety settings. Also refer to the Safety guidance to learn how to incorporate
 *   safety considerations in your AI applications.
 * @param systemInstruction
 *   Optional. Developer set system instruction(s). Currently, text only.
 * @param generationConfig
 *   Optional. Configuration options for model generation and outputs.
 * @param cachedContent
 *   Optional. The name of the content cached to use as context to serve the prediction.
 *   Format: cachedContents/{cachedContent}
 */
case class GenerateContentSettings(
  model: String,
  tools: Option[Seq[Tool]] = None,
  toolConfig: Option[ToolConfig] = None,
  safetySettings: Option[Seq[SafetySetting]] = None,
  systemInstruction: Option[Content] = None,
  generationConfig: Option[GenerationConfig] = None,
  cachedContent: Option[String] = None
)

/**
 * Safety setting, affecting the safety-blocking behavior. Passing a safety setting for a
 * category changes the allowed probability that content is blocked.
 * @param category
 *   Required. The category for this setting.
 * @param threshold
 *   Required. Controls the probability threshold at which harm is blocked.
 */
case class SafetySetting(
  category: HarmCategory,
  threshold: HarmBlockThreshold
)

sealed trait ToolConfig

object ToolConfig {

  /**
   * @param mode
   *   Optional. Specifies the mode in which function calling should execute. If unspecified,
   *   the default value will be set to AUTO.
   * @param allowedFunctionNames
   *   Optional. A set of function names that, when provided, limits the functions the model
   *   will call. This should only be set when the Mode is ANY. Function names should match
   *   [FunctionDeclaration.name]. With mode set to ANY, model will predict a function call
   *   from the set of function names provided.
   */
  case class FunctionCallingConfig(
    mode: Option[FunctionCallingMode],
    allowedFunctionNames: Option[Seq[String]]
  ) extends ToolConfig
}

sealed trait FunctionCallingMode extends EnumValue

object FunctionCallingMode {

  // Unspecified function calling mode. This value should not be used.
  case object MODE_UNSPECIFIED extends FunctionCallingMode
  // Default model behavior, model decides to predict either a function call or a natural language response.
  case object AUTO extends FunctionCallingMode
  // Model is constrained to always predicting a function call only. If "allowedFunctionNames" are set, the predicted function call will be limited to any one of "allowedFunctionNames", else the predicted function call will be any one of the provided "functionDeclarations".
  case object ANY extends FunctionCallingMode
  // Model will not predict any function call. Model behavior is same as when not passing any function declarations.
  case object NONE extends FunctionCallingMode

  def values: Seq[FunctionCallingMode] = Seq(
    MODE_UNSPECIFIED,
    AUTO,
    ANY,
    NONE
  )
}
