package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.{EnumValue, RunTool}

import java.{util => ju}

/**
 * Represents an execution run on a thread.
 *
 * @param id
 *   The identifier, which can be referenced in API endpoints.
 * @param created_at
 *   The Unix timestamp (in seconds) for when the run was created.
 * @param thread_id
 *   The ID of the thread that was executed on as a part of this run.
 * @param assistant_id
 *   The ID of the assistant used for execution of this run.
 * @param status
 *   The status of the run, which can be either queued, in_progress, requires_action,
 *   cancelling, cancelled, failed, completed, or expired.
 * @param required_action
 *   Details on the action required to continue the run. Will be null if no action is required.
 * @param last_error
 *   The last error associated with this run. Will be null if there are no errors.
 * @param expires_at
 *   The Unix timestamp (in seconds) for when the run will expire.
 * @param started_at
 *   The Unix timestamp (in seconds) for when the run was started.
 * @param cancelled_at
 *   The Unix timestamp (in seconds) for when the run was cancelled.
 * @param failed_at
 *   The Unix timestamp (in seconds) for when the run failed.
 * @param completed_at
 *   The Unix timestamp (in seconds) for when the run was completed.
 * @param model
 *   The model that the assistant used for this run.
 * @param instructions
 *   The instructions that the assistant used for this run.
 * @param tools
 *   The list of tools that the assistant used for this run.
 * @param file_ids
 *   The list of File IDs the assistant used for this run.
 * @param metadata
 *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
 *   storing additional information about the object in a structured format. Keys can be a
 *   maximum of 64 characters long and values can be a maximum of 512 characters long.
 * @param usage
 */
final case class Run(
  id: String,
  created_at: ju.Date,
  thread_id: String,
  assistant_id: String,
  status: Run.Status,
  required_action: Option[String],
  last_error: Option[Run.Error],
  expires_at: ju.Date,
  started_at: Option[ju.Date],
  cancelled_at: Option[ju.Date],
  failed_at: Option[ju.Date],
  completed_at: Option[ju.Date],
  model: String,
  instructions: String,
  tools: List[RunTool],
  file_ids: List[String],
  metadata: Map[String, String],
  usage: Option[Run.Usage]
)

object Run {
  sealed trait Status extends EnumValue

  object Status {
    case object queued extends Status
    case object in_progress extends Status
    case object required_action extends Status
    case object cancelling extends Status
    case object failed extends Status
    case object completed extends Status
    case object expired extends Status

    def values: Seq[Status] =
      Seq(queued, in_progress, required_action, cancelling, failed, completed, expired)

  }

  /**
   * Details on the action required to continue the run. Will be null if no action is required.
   * @param submit_tool_outputs
   *   Details on the tool outputs needed for this run to continue.
   */
  final case class Action(submit_tool_outputs: Run.SubmitToolOutputs)

  /**
   * @param tool_calls
   *   A list of the relevant tool calls.
   */
  final case class SubmitToolOutputs(tool_calls: List[ToolCall])

  /**
   * @param code
   *   One of server_error, rate_limit_exceeded, or invalid_prompt.
   * @param message
   *   A human-readable description of the error.
   */
  final case class Error(
    code: Error.Code,
    message: String
  )

  object Error {
    sealed trait Code extends EnumValue

    object Code {
      case object invalid_prompt extends Code
      case object rate_limit_exceeded extends Code
      case object server_error extends Code

      def values: Seq[Code] = Seq(invalid_prompt, rate_limit_exceeded, server_error)
    }
  }

  /**
   * @param completion_tokens
   *   Number of completion tokens used over the course of the run.
   * @param prompt_tokens
   *   Number of prompt tokens used over the course of the run.
   * @param total_tokens
   *   Total number of tokens used (prompt + completion).
   */
  final case class Usage(
    completion_tokens: Int,
    prompt_tokens: Int,
    total_tokens: Int
  )

}
