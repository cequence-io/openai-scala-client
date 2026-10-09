package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.domain.decisions.{
  CreateDecisionSettings,
  Decision,
  DecisionInput,
  DecisionQuestion
}
import io.cequence.openaiscala.service.OpenAIDecisionsService
import io.cequence.openaiscala.typesafe.service.TypeSafeService

import scala.concurrent.{ExecutionContext, Future}

/**
 * OpenAI's Decisions API interface over any System One service (e.g. one wrapped by the retry
 * adapter): the questions translated by [[OpenAIToSystemOne]], the failures repacked as
 * `OpenAIScala*` exceptions with the native one as the cause.
 *
 * @param imageInput
 *   whether the service's host reads images - else an input with images is refused
 */
private[service] final class OpenAIDecisionsOverTypeSafe(
  underlying: TypeSafeService,
  imageInput: Boolean
)(
  implicit ec: ExecutionContext
) extends OpenAIDecisionsService {

  private val imageRefusal =
    if (imageInput) None
    else
      Some(
        "Images go only to a decision service whose host reads them - " +
          "TypeSafeServiceFactory.asOpenAIDecisions(service, imageInput = true) for one that does."
      )

  override def createDecision(
    input: DecisionInput,
    questions: Seq[DecisionQuestion],
    settings: CreateDecisionSettings
  ): Future[Decision] =
    OpenAIToSystemOne
      .createDecision(underlying, imageRefusal)(input, questions, settings)
      .recoverWith(repackAsOpenAIException)

  override def close(): Unit = underlying.close()
}
