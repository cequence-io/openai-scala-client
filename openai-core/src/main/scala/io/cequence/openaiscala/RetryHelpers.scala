package io.cequence.openaiscala

import akka.actor.Scheduler
import akka.pattern.after
import io.cequence.openaiscala.RetryHelpers.{RetrySettings, retry}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object RetryHelpers {
  private[openaiscala] def delay(
    n: Integer
  )(
    implicit retrySettings: RetrySettings
  ): FiniteDuration =
    FiniteDuration(
      scala.math.round(
        retrySettings.delayOffset.length + scala.math.pow(
          retrySettings.delayBase,
          n.doubleValue()
        )
      ),
      retrySettings.delayOffset.unit
    )

  private[openaiscala] def retry[T](
    fun: () => Future[T],
    maxAttempts: Int,
    failureMessage: Option[String] = None,
    log: Option[String => Unit] = Some(println),
    isRetryable: Throwable => Boolean = {
      case Retryable(_) => true
      case _            => false
    }
  )(
    implicit ec: ExecutionContext,
    scheduler: Scheduler,
    retrySettings: RetrySettings
  ): Future[T] = {
    def retryAux(attempt: Int): Future[T] =
      try {
        if (attempt < maxAttempts) {
          fun().recoverWith {
            case e: Throwable if isRetryable(e) =>
              log.foreach(
                _(
                  s"${failureMessage.map(_ + ". ").getOrElse("")}Attempt ${attempt}. Retrying..."
                )
              )

              after(delay(attempt - 1), scheduler) {
                retryAux(attempt + 1)
              }
          }
        } else {
          fun()
        }
      } catch {
        case NonFatal(error) => Future.failed(error)
      }

    retryAux(1)
  }

  final case class RetrySettings(
    maxRetries: Int = 5,
    delayOffset: FiniteDuration = 2.seconds,
    delayBase: Double = 2
  ) {
    def constantInterval(interval: FiniteDuration): RetrySettings =
      copy(delayBase = 0).copy(delayOffset = interval)
  }

  object RetrySettings {
    def apply(interval: FiniteDuration): RetrySettings =
      RetrySettings().constantInterval(
        interval
      )
  }
}

trait RetryHelpers {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // TODO: would be better to reevaluate the future otherwise we handle give a chance only to "external" exceptions
  implicit class FutureWithRetry[T](f: Future[T]) {

    def retryOnFailure(
      failureMessage: Option[String] = None,
      log: Option[String => Unit] = Some(println),
      isRetryable: Throwable => Boolean = {
        case Retryable(_) => true
        case _            => false
      }
    )(
      implicit retrySettings: RetrySettings,
      ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[T] = {
      retry(
        () => f,
        maxAttempts = retrySettings.maxRetries + 1,
        failureMessage,
        log,
        isRetryable
      )
    }
  }

  implicit class FutureWithFailover[IN, T](
    f: IN => Future[T]
  ) {
    def retryOnFailureOrFailover(
      normalAndFailoverInputsAndMessages: Seq[(IN, String)], // input and string for logging
      failureMessage: Option[String] = None,
      log: Option[String => Unit] = Some(println),
      isRetryable: Throwable => Boolean = {
        case Retryable(_) => true
        case _            => false
      }
    )(
      implicit retrySettings: RetrySettings,
      ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[T] =
      retryOnFailureOrFailoverAux(
        None,
        normalAndFailoverInputsAndMessages,
        failureMessage,
        log,
        isRetryable
      )

    private def retryOnFailureOrFailoverAux(
      lastException: Option[Throwable],
      inputsAndMessagesToTryInOrder: Seq[(IN, String)],
      failureMessage: Option[String] = None,
      log: Option[String => Unit] = Some(println),
      isRetryable: Throwable => Boolean = {
        case Retryable(_) => true
        case _            => false
      }
    )(
      implicit retrySettings: RetrySettings,
      ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[T] = {
      assert(
        inputsAndMessagesToTryInOrder.nonEmpty || lastException.nonEmpty,
        "At least one input or last exception must be defined!"
      )

      inputsAndMessagesToTryInOrder match {
        case Nil =>
          val lastExceptionActual = lastException.getOrElse(
            throw new OpenAIScalaClientException(
              s"Should never happen. No last exception provided!"
            )
          )

          logger.error(
            s"No more failover inputs to try! Throwing the last error: ${lastExceptionActual.getMessage}"
          )

          Future.failed(lastExceptionActual)

        case _ =>
          val (input, inputLogMessage) = inputsAndMessagesToTryInOrder.head

          f(input)
            .retryOnFailure(
              failureMessage.map(message => s"${inputLogMessage} - ${message}"),
              log,
              isRetryable
            )
            .recoverWith { case e: Throwable =>
              val errorMessage = failureMessage
                .map(message => s"${inputLogMessage} - ${message} after retries!")
                .getOrElse(
                  s"${inputLogMessage} failed after retries!"
                )

              logger.error(
                s"$errorMessage Initiating failover to ${inputsAndMessagesToTryInOrder.tail.map(_._2).headOption.getOrElse("N/A")}.",
                e
              )

              retryOnFailureOrFailoverAux(
                Some(e),
                inputsAndMessagesToTryInOrder.tail,
                failureMessage,
                log,
                isRetryable
              )
            }
      }
    }
  }
}
