package io.cequence.openaiscala.testkit

import io.cequence.wsclient.stream.{AsyncStream, StreamDiagnostics}

import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

/**
 * Runs streams in specs through the selected [[StreamRunner]] (`-Dopenai.test.consumer`), so a
 * spec written once runs under every consumer of the engine matrix.
 */
trait StreamSpecSupport {

  protected def streamTimeout: FiniteDuration = 30.seconds

  protected def streamRunner: StreamRunner = StreamRunner.current

  protected def runStream[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): immutable.Seq[A] =
    Await.result(streamRunner.runToSeq(stream), timeout)

  /** The failure of a run (fails the spec if the run completes). */
  protected def runStreamFailure[A](
    stream: AsyncStream[A],
    timeout: FiniteDuration = streamTimeout
  )(
    implicit ec: ExecutionContext
  ): Throwable =
    Try(Await.result(streamRunner.runToSeq(stream), timeout)) match {
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
    Await.result(streamRunner.runTake(stream, n), timeout)

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
