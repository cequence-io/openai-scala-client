package io.cequence.openaiscala.testkit

import io.cequence.wsclient.stream.{AsyncStream, StreamDiagnostics}

import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Runs streams in specs through the selected [[StreamRunner]] (`-Dopenai.test.consumer`), so a
 * spec written once runs under every consumer of the engine matrix. With
 * `-Dopenai.test.expectedBridges=<n>` every run through `runStream` / `runStreamFailure` /
 * `runStreamTake` / `runStreamDrain` also asserts the bridges it built - the matrix sets 0
 * where the engine and the consumer are the same library, turning every wire spec into a
 * native round-trip check.
 */
trait StreamSpecSupport {

  protected def streamTimeout: FiniteDuration = 30.seconds

  protected def streamRunner: StreamRunner = StreamRunner.current

  protected def expectedBridges: Option[Long] = StreamRunner.expectedBridges

  protected def runStream[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): immutable.Seq[A] =
    awaitChecked(streamRunner.runToSeq(stream), timeout).get

  /** The failure of a run (fails the spec if the run completes). */
  protected def runStreamFailure[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): Throwable =
    awaitChecked(streamRunner.runToSeq(stream), timeout) match {
      case Failure(e) =>
        e

      case Success(elements) =>
        throw new AssertionError(
          s"Expected the stream to fail, but it completed with ${elements.size} element(s)"
        )
    }

  protected def runStreamTake[A](
    stream: AsyncStream[A],
    n: Int,
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): immutable.Seq[A] =
    awaitChecked(streamRunner.runTake(stream, n), timeout).get

  /** Runs the stream to its end, keeping no element (for streams too large to collect). */
  protected def runStreamDrain[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): Unit =
    awaitChecked(streamRunner.runDrain(stream), timeout).get

  // the run's outcome; with an expected bridge count, the run is counted and checked first (a
  // count off is an AssertionError even when the run itself failed)
  private def awaitChecked[A](
    run: => Future[A],
    timeout: FiniteDuration
  ): Try[A] =
    expectedBridges match {
      case None =>
        Try(Await.result(run, timeout))

      case Some(expected) =>
        // a runner that throws while compiling fails the run like the neutral path does
        val (future, bridges) = StreamDiagnostics.counted(Future.fromTry(Try(run)).flatten)
        val outcome = Try(Await.result(future, timeout))
        if (bridges.get != expected)
          throw new AssertionError(
            s"Expected $expected bridge(s) on ${streamRunner.name}, the run built ${bridges.get}" +
              outcome.failed.map(e => s" (the run failed: $e)").getOrElse("")
          )
        outcome
    }

  /** Runs the stream and counts the bridges its run built (scoped to this run). */
  protected def runStreamCounted[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): (immutable.Seq[A], Long) = {
    val (future, bridges) = StreamDiagnostics.counted(streamRunner.runToSeq(stream))
    val elements = Await.result(future, timeout)
    (elements, bridges.get)
  }
}

object StreamSpecSupport extends StreamSpecSupport
