package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.Run.Reason
import io.cequence.openaiscala.domain.Run.TruncationStrategyType.Auto
import io.cequence.openaiscala.domain.response.UsageInfo
import io.cequence.wsclient.domain.SnakeCaseEnumValue

import java.util.Date

sealed trait RunStatus extends SnakeCaseEnumValue

object RunStatus {
  case object Queued extends RunStatus
  case object InProgress extends RunStatus
  case object RequiresAction extends RunStatus
  case object Cancelling extends RunStatus
  case object Cancelled extends RunStatus
  case object Failed extends RunStatus
  case object Completed extends RunStatus
  case object Incomplete extends RunStatus
  case object Expired extends RunStatus

  def finishedStates: Set[RunStatus] =
    Set(
      Completed,
      Failed,
      Cancelled,
      Incomplete, // TODO: is this a finished state?
      Expired
    )
}

sealed trait ToolChoice
object ToolChoice {
  case object None extends ToolChoice
  case object Auto extends ToolChoice
  case object Required extends ToolChoice
  case class EnforcedTool(spec: RunTool) extends ToolChoice
}

case class RequiredAction(
  `type`: String,
  submit_tool_outputs: SubmitToolOutputs
)

case class SubmitToolOutputs(
  tool_calls: Seq[ToolCall]
)

case class ToolCall(
  id: String,
  `type`: String,
  function: FunctionCallSpec
)

case class Run(
  id: String,
  `object`: String,
  created_at: Date,
  thread_id: String, // path
  assistant_id: String, // param
  status: RunStatus,
  required_action: Option[RequiredAction],
  last_error: Option[LastError],
  expires_at: Option[Date],
  started_at: Option[Date],
  cancelled_at: Option[Date],
  failed_at: Option[Date],
  completed_at: Option[Date],
  incomplete_details: Option[Reason],
  model: String,
  instructions: Option[String],
  tools: Seq[AssistantTool],
  usage: Option[UsageInfo]
  //  tool_choice: Either[String, Any], // Replace Any with the actual type when available
  //  response_format: Either[String, Any] // Replace Any with the actual type when available
) {
  def isFinished: Boolean = RunStatus.finishedStates.contains(status)
}

object Run {

  case class TruncationStrategy(
    `type`: TruncationStrategyType = Auto,
    lastMessages: Option[Int]
  )

  sealed trait TruncationStrategyType extends SnakeCaseEnumValue
  object TruncationStrategyType {
    case object Auto extends TruncationStrategyType
    case object LastMessages extends TruncationStrategyType
  }

  case class Reason(reason: String)

  sealed trait LastErrorCode extends SnakeCaseEnumValue

  object LastErrorCode {
    case object ServerError extends LastErrorCode
    case object RateLimitExceeded extends LastErrorCode
    case object InvalidPrompt extends LastErrorCode
  }

}
