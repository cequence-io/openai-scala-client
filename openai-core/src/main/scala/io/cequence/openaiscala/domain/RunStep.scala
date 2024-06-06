package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.response.UsageInfo
import io.cequence.wsclient.domain.EnumValue

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
  lastError: Option[
    GenericLastError[RunStep.LastError]
  ],
  expiredAt: Option[Date],
  cancelledAt: Option[Date],
  failedAt: Option[Date],
  completedAt: Option[Date],
  metadata: Option[Map[String, String]],
  usage: Option[UsageInfo]
)

object RunStep {

  sealed trait LastError extends EnumValue
  object LastError {
    case object ServerError extends LastError
    case object RateLimitExceeded extends LastError
  }

}
