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
   * Indicates that the model should use a built-in tool to generate a response.
   *
   * @param `type`
   *   The type of hosted tool the model should use.
   */
  case class HostedTool(`type`: String) extends ToolChoice

  object HostedTool {
    val FileSearch: HostedTool = HostedTool("file_search")
    val WebSearchPreview: HostedTool = HostedTool("web_search_preview")
    val ComputerUsePreview: HostedTool = HostedTool("computer_use_preview")
  }

  /**
   * Used to force the model to call a specific function.
   *
   * @param name
   *   The name of the function to call.
   */
  case class FunctionTool(name: String) extends ToolChoice {
    val `type`: String = "function"
  }
}
