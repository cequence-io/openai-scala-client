package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.response.UsageInfo
import io.cequence.wsclient.domain.{EnumValue, SnakeCaseEnumValue}

import java.util.Date

case class RunStep(
  id: String,
  `object`: String,
  createdAt: Date,
  assistantId: String,
  threadId: String,
  runId: String,
  `type`: String,
  status: String,
  stepDetails: Option[StepDetail],
//  lastError: Map[String, String],
  lastError: Option[LastError],
  expiredAt: Option[Date],
  cancelledAt: Option[Date],
  failedAt: Option[Date],
  completedAt: Option[Date],
  metadata: Option[Map[String, String]],
  usage: Option[UsageInfo]
)

object RunStep {

//  case class LastError(
//    code: LastErrorCode,
//    message: String
//  )
//
//  sealed trait LastErrorCode extends SnakeCaseEnumValue
//  object LastErrorCode {
//    case object ServerError extends LastErrorCode
//    case object RateLimitExceeded extends LastErrorCode
//  }

}
