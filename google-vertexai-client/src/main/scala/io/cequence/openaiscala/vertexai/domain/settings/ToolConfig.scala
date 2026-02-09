package io.cequence.openaiscala.vertexai.domain.settings

import io.cequence.wsclient.domain.EnumValue

/**
 * Tool configuration for controlling function calling behavior in Vertex AI.
 */
sealed trait ToolConfig

object ToolConfig {

  /**
   * Configuration for function calling behavior.
   *
   * @param mode
   *   Optional. Specifies the mode in which function calling should execute.
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
  // Model is constrained to always predicting a function call only.
  case object ANY extends FunctionCallingMode
  // Model will not predict any function call.
  case object NONE extends FunctionCallingMode

  def values: Seq[FunctionCallingMode] = Seq(
    MODE_UNSPECIFIED,
    AUTO,
    ANY,
    NONE
  )
}
