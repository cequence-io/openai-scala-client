package io.cequence.openaiscala.testkit

import io.cequence.wsclient.stream.{AsyncStream, StreamDiagnostics}

import java.util.concurrent.TimeoutException
import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Runs streams in specs through the selected [[StreamRunner]] (`-Dopenai.test.consumer`), so a
 * spec written once runs under every consumer of the engine matrix. With
 * `-Dopenai.test.expectedBridges=<n>` every run through `runStream` / `runStreamFailure` /
 * `runStreamTake` / `runStreamHead` / `runStreamDrain` / `startStream` also asserts the
 * bridges it built - the matrix sets 0 where the engine and the consumer are the same library,
 * turning every wire spec into a native round-trip check. A run that has not ended within its
 * timeout fails the spec (an AssertionError) - it never counts as the run's own outcome.
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

  /** The failure of a run (fails the spec if the run completes, or does not end in time). */
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

  /** The first element; the run is cancelled after it (fails the spec on an empty stream). */
  protected def runStreamHead[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): A =
    runStreamTake(stream, 1, timeout).headOption.getOrElse(
      throw new AssertionError("Expected an element, but the stream ended without one")
    )

  /** Runs the stream to its end, keeping no element (for streams too large to collect). */
  protected def runStreamDrain[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): Unit =
    awaitChecked(streamRunner.runDrain(stream), timeout).get

  /**
   * Starts a run without waiting for it - for a spec that acts while the run is going (e.g.
   * sends a turn once the run has subscribed). The bridges are checked once the run ended: a
   * count off fails the returned future.
   */
  protected def startStream[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[immutable.Seq[A]] =
    expectedBridges match {
      case None =>
        started(streamRunner.runToSeq(stream))

      case Some(expected) =>
        val (run, bridges) = StreamDiagnostics.counted(started(streamRunner.runToSeq(stream)))
        run.transform { outcome =>
          if (bridges.get != expected)
            Failure(bridgeError(expected, bridges.get, outcome))
          else
            outcome
        }
    }

  /** Runs the stream and counts the bridges its run built (scoped to this run). */
  protected def runStreamCounted[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): (immutable.Seq[A], Long) = {
    val (future, bridges) = StreamDiagnostics.counted(started(streamRunner.runToSeq(stream)))
    val elements = await(future, timeout).get
    (elements, bridges.get)
  }

  // the run's outcome; with an expected bridge count, the run is counted and checked first (a
  // count off is an AssertionError even when the run itself failed)
  private def awaitChecked[A](
    run: => Future[A],
    timeout: FiniteDuration
  ): Try[A] =
    expectedBridges match {
      case None =>
        await(started(run), timeout)

      case Some(expected) =>
        val (future, bridges) = StreamDiagnostics.counted(started(run))
        val outcome = await(future, timeout)
        if (bridges.get != expected)
          throw bridgeError(expected, bridges.get, outcome)
        outcome
    }

  // a runner that throws while compiling fails the run like a failed future
  private def started[A](run: => Future[A]): Future[A] =
    Future.fromTry(Try(run)).flatten

  // the run's own outcome - a run still going after `timeout` fails the spec instead
  private def await[A](
    future: Future[A],
    timeout: FiniteDuration
  ): Try[A] =
    Try(Await.ready(future, timeout)) match {
      case Failure(e: TimeoutException) =>
        throw new AssertionError(s"The stream run did not end within $timeout", e)

      case Failure(e) =>
        throw e

      case Success(_) =>
        future.value.getOrElse(
          Failure(new IllegalStateException("A ready future has no value"))
        )
    }

  private def bridgeError(
    expected: Long,
    built: Long,
    outcome: Try[_]
  ): AssertionError =
    new AssertionError(
      s"Expected $expected bridge(s) on ${streamRunner.name}, the run built $built" +
        outcome.failed.map(e => s" (the run failed: $e)").getOrElse("")
    )
}

object StreamSpecSupport extends StreamSpecSupport
