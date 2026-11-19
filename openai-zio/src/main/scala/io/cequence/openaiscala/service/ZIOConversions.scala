package io.cequence.openaiscala.service

import io.cequence.wsclient.stream.ZioInterop._
import io.cequence.wsclient.stream.{AsyncCall, AsyncStream, Bytes}
import zio.stream.ZStream
import zio.{Runtime, Task, Unsafe, ZIO}

import scala.concurrent.{ExecutionContext, Future}

/** The conversions the generated ZIO views use (since 2.0.0). */
private[openaiscala] object ZIOConversions {

  /**
   * A Future-returning call as a lazy Task: the call runs per run of the Task
   * (`ZIO.fromFuture` with the fiber's executor) - as a Future leaf of an `AsyncCall`, so the
   * hop counts as a bridge in `CallDiagnostics.counted`.
   */
  def task[A](call: ExecutionContext => Future[A]): Task[A] =
    AsyncCall.fromFuture(call).toZIO

  /**
   * A lazy call as a Task (since 2.1.0) - compiled natively: a call whose leaf is a ZIO effect
   * (the zio-http engine's request) comes back as that effect, with the operators as ZIO
   * stages, so interrupting the Task aborts the request; a Future leaf costs one bridge.
   */
  def call[A](call: AsyncCall[A]): Task[A] =
    call.toZIO

  /**
   * A stream as a ZStream - a stream whose source is a native ZIO stream (the zio-http
   * engine's) comes back as that stream, with the operators as ZIO stages.
   */
  def stream[A](stream: AsyncStream[A]): ZStream[Any, Throwable, A] =
    stream.toZStream

  /**
   * A byte stream as ZIO's byte stream - over the zio-http engine's body that is its own chunk
   * stream (no bridge, no copy).
   */
  def bytes(stream: AsyncStream[Bytes]): ZStream[Any, Throwable, Byte] =
    stream.toZByteStream

  /** A ZIO callback as the Future callback a service takes, run on `runtime`. */
  def callback[A, B](
    f: A => Task[B]
  )(
    runtime: Runtime[Any]
  ): A => Future[B] =
    a => Unsafe.unsafe(implicit unsafe => runtime.unsafe.runToFuture(f(a)))

  /** A stream built with the executing fiber's ExecutionContext (per run). */
  def streamWithEc[A](
    build: ExecutionContext => ZStream[Any, Throwable, A]
  ): ZStream[Any, Throwable, A] =
    ZStream.unwrap(ZIO.executor.map(executor => build(executor.asExecutionContext)))

  /** Runs a builder that needs an ExecutionContext on the runtime's executor. */
  def withEc[A](runtime: Runtime[Any])(build: ExecutionContext => A): A =
    build(
      Unsafe.unsafe(implicit unsafe =>
        runtime.unsafe.run(ZIO.executor.map(_.asExecutionContext)).getOrThrowFiberFailure()
      )
    )
}
