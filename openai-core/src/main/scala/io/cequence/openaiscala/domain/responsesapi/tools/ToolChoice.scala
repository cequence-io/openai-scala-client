package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.wsclient.domain.EnumValue

/**
 * Represents the tool choice mode for controlling which (if any) tool is called by the model.
 */
sealed trait ToolChoice

object ToolChoice {

  sealed trait Mode extends ToolChoice with EnumValue {
    override def toString: String = super.toString.toLowerCase
  }

  object Mode {

    /**
     * The model will not call any tool and instead generates a message.
     */
    case object None extends Mode

    /**
     * The model can pick between generating a message or calling one or more tools.
     */
    case object Auto extends Mode

    /**
     * The model must call one or more tools.
     */
    case object Required extends Mode

    def values: Seq[Mode] = Seq(
      None,
      Auto,
      Required
    )
  }

  /**
   * Constrains the tools available to the model to a pre-defined set.
   *
   * @param mode
   *   Constrains the tools available to the model. "auto" allows the model to pick from among
   *   the allowed tools and generate a message. "required" requires the model to call one or
   *   more of the allowed tools.
   * @param tools
   *   A list of tool definitions that the model should be allowed to call.
   */
  case class AllowedTools(
    mode: String, // "auto" or "required"
    tools: Seq[Tool]
  ) extends ToolChoice {
    val `type`: String = "allowed_tools"
  }

  object AllowedTools {

    /**
     * Create an AllowedTools with auto mode.
     */
    def auto(tools: Seq[Tool]): AllowedTools = AllowedTools("auto", tools)

    /**
     * Create an AllowedTools with required mode.
     */
    def required(tools: Seq[Tool]): AllowedTools = AllowedTools("required", tools)
  }

  /**
   * The type of hosted tool the model should use.
   */
  sealed trait HostedToolType extends EnumValue

  object HostedToolType {
    case object file_search extends HostedToolType
    case object web_search_preview extends HostedToolType
    case object computer_use_preview extends HostedToolType
    case object code_interpreter extends HostedToolType
    case object image_generation extends HostedToolType

    def values: Seq[HostedToolType] = Seq(
      file_search,
      web_search_preview,
      computer_use_preview,
      code_interpreter,
      image_generation
    )
  }

  /**
   * Indicates that the model should use a built-in tool to generate a response.
   *
   * @param `type`
   *   The type of hosted tool the model should use. Allowed values are: file_search,
   *   web_search_preview, computer_use_preview, code_interpreter, image_generation.
   */
  case class HostedTool(
    `type`: HostedToolType
  ) extends ToolChoice

  /**
   * Use this option to force the model to call a specific function.
   *
   * @param name
   *   The name of the function to call.
   */
  case class FunctionTool(
    name: String
  ) extends ToolChoice {
    val `type`: String = "function"
  }

  /**
   * Use this option to force the model to call a specific tool on a remote MCP server.
   *
   * @param serverLabel
   *   The label of the MCP server to use.
   * @param name
   *   The name of the tool to call on the server. Optional.
   */
  case class MCPTool(
    serverLabel: String,
    name: Option[String] = None
  ) extends ToolChoice {
    val `type`: String = "mcp"
  }

  /**
   * Use this option to force the model to call a specific custom tool.
   *
   * @param name
   *   The name of the custom tool to call.
   */
  case class CustomTool(
    name: String
  ) extends ToolChoice {
    val `type`: String = "custom"
  }

}
