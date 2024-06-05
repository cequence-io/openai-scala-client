package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.Run.Reason
import io.cequence.openaiscala.domain.Run.TruncationStrategyType.Auto
import io.cequence.openaiscala.domain.response.UsageInfo
import io.cequence.wsclient.domain.{EnumValue, SnakeCaseEnumValue}
import play.api.libs.json.JsonNaming.SnakeCase

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
}

case class RequiredAction(
  `type`: String,
  submitToolOutputs: SubmitToolOutputs
)

case class SubmitToolOutputs(
  toolCalls: Seq[ToolCall]
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
  thread_id: String,
  assistant_id: String,
  status: RunStatus,
  required_action: Option[RequiredAction],
  last_error: Option[Run.LastErrorCode],
  expires_at: Option[Date],
  started_at: Option[Date],
  cancelled_at: Option[Date],
  failed_at: Option[Date],
  completed_at: Option[Date],
  incomplete_details: Option[Reason],
  model: String,
  instructions: String,
  usage: Option[UsageInfo]
//  tool_choice: Either[String, Any], // Replace Any with the actual type when available
//  response_format: Either[String, Any] // Replace Any with the actual type when available
)

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

  case class Reason(value: String) extends AnyVal

  sealed trait LastErrorCode extends SnakeCaseEnumValue

  // server_error, rate_limit_exceeded, or invalid_prompt
  object LastErrorCode {
    case object ServerError extends LastErrorCode
    case object RateLimitExceeded extends LastErrorCode
    case object InvalidPrompt extends LastErrorCode
  }

}
