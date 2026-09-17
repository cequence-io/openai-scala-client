package io.cequence.openaiscala.typesafe.service

import akka.actor.Scheduler
import io.cequence.openaiscala.RetryHelpers
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.typesafe.domain.{ModelMetadata, Question, SystemOneResponse}
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

/** Adapters composable over a [[TypeSafeService]]. */
object TypeSafeServiceAdapters {

  /**
   * Retries failed calls with exponential backoff, as TypeSafe asks for 429 / 529 responses.
   * What is retried is decided by [[io.cequence.openaiscala.Retryable]] (rate limits,
   * overloads, 5xx and timeouts - never auth or validation errors).
   *
   * @param log
   *   where to report each retry (nothing by default)
   */
  def retry(
    underlying: TypeSafeService,
    log: Option[String => Unit] = None,
    includeExceptionMessage: Boolean = false
  )(
    implicit ec: ExecutionContext,
    retrySettings: RetrySettings,
    scheduler: Scheduler
  ): TypeSafeService =
    new RetryTypeSafeService(underlying, log, includeExceptionMessage)
}

private class RetryTypeSafeService(
  underlying: TypeSafeService,
  log: Option[String => Unit],
  includeExceptionMessage: Boolean
)(
  implicit ec: ExecutionContext,
  retrySettings: RetrySettings,
  scheduler: Scheduler
) extends TypeSafeService
    with RetryHelpers {

  override def defaultModel: String = underlying.defaultModel

  override def systemOne(
    state: JsValue,
    questions: Map[String, Question],
    model: String
  ): Future[SystemOneResponse] =
    underlying
      .systemOne(state, questions, model)
      .retryOnFailure(
        Some("SystemOne call failed"),
        log,
        isRetryable = retryable,
        includeExceptionMessage = includeExceptionMessage
      )

  override def listModels: Future[Seq[ModelMetadata]] =
    underlying.listModels.retryOnFailure(
      Some("ListModels call failed"),
      log,
      isRetryable = retryable,
      includeExceptionMessage = includeExceptionMessage
    )

  // the native exceptions, not the OpenAI ones the shared Retryable matcher knows
  private val retryable: Throwable => Boolean = {
    case TypeSafeRetryable(_) => true
    case _                    => false
  }

  override def close(): Unit = underlying.close()
}
