package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.EnumValue

/**
 * Represents a step in execution of a run.
 *
 * @param id
 *   The identifier of the run step, which can be referenced in API endpoints.
 * @param created_at
 *   The Unix timestamp (in seconds) for when the run step was created.
 * @param assistant_id
 *   The ID of the assistant associated with the run step.
 * @param thread_id
 *   The ID of the thread that was run.
 * @param run_id
 *   The ID of the run that this run step is a part of.
 * @param `type`
 *   The type of run step, which can be either message_creation or tool_calls.
 * @param status
 *   The status of the run step, which can be either in_progress, cancelled, failed, completed,
 *   or expired.
 * @param step_details
 *   The details of the run step.
 * @param last_error
 *   The last error associated with this run step. Will be null if there are no errors.
 * @param expired_at
 *   The Unix timestamp (in seconds) for when the run step expired. A step is considered
 *   expired if the parent run is expired.
 * @param cancelled_at
 *   The Unix timestamp (in seconds) for when the run step was cancelled.
 * @param failed_at
 *   The Unix timestamp (in seconds) for when the run step failed.
 * @param completed_at
 *   The Unix timestamp (in seconds) for when the run step completed.
 * @param metadata
 *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
 *   storing additional information about the object in a structured format. Keys can be a
 *   maximum of 64 characters long and values can be a maximum of 512 characters long.
 * @param usage
 *   Usage statistics related to the run step. This value will be null while the run step's
 *   status is in_progress.
 */
final case class RunStep(
  id: String,
  created_at: java.util.Date,
  assistant_id: String,
  thread_id: String,
  run_id: String,
  `type`: RunStep.Type,
  status: RunStep.Status,
  step_details: RunStep.Details,
  last_error: Option[RunStep.Error],
  expired_at: java.util.Date,
  cancelled_at: Option[java.util.Date],
  failed_at: Option[java.util.Date],
  completed_at: Option[java.util.Date],
  metadata: Map[String, String],
  usage: Run.Usage
)

object RunStep {

  sealed trait Type extends EnumValue
  object Type {
    case object message_creation extends Type
    case object tool_calls extends Type

    def values: Seq[Type] = Seq(message_creation, tool_calls)
  }

  sealed trait Status extends EnumValue
  object Status {
    case object in_progress extends Status

    case object cancelled extends Status

    case object failed extends Status

    case object completed extends Status

    def values: Seq[Status] = Seq(in_progress, cancelled, failed, completed)
  }

  final case class Details(
    messageId: String,
    // TODO: proper toolCalls model
    toolCalls: String
  )

  final case class Error(
    code: Error.Code,
    message: String
  )

  object Error {
    sealed trait Code extends EnumValue

    object Code {
      case object rate_limit_exceeded extends Code
      case object server_error extends Code

      def values: Seq[Code] = Seq(rate_limit_exceeded, server_error)
    }
  }

}
