package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.ResponseHeaders
import io.cequence.openaiscala.domain.decisions.JsonFormats._
import io.cequence.openaiscala.domain.decisions.{
  CreateDecisionSettings,
  Decision,
  DecisionInput,
  DecisionQuestion
}
import io.cequence.openaiscala.service.OpenAIDecisionsService
import io.cequence.wsclient.ResponseImplicits._

import scala.concurrent.Future

/** The Decisions API (`POST /v1/decisions`). */
trait OpenAIDecisionsServiceImpl extends OpenAIDecisionsService with OpenAIServiceWSBase {

  override def createDecision(
    input: DecisionInput,
    questions: Seq[DecisionQuestion],
    settings: CreateDecisionSettings
  ): Future[Decision] =
    execPOSTBodyRich(
      EndPoint.decisions,
      body = createDecisionBody(input, questions, settings)
    ).map { rich =>
      // the response body carries no id - the request id is the `x-request-id` header
      val requestId = ResponseHeaders.first(rich.headers, Seq("x-request-id"))
      getResponseOrError(rich).asSafeJson[Decision].copy(requestId = requestId)
    }
}
