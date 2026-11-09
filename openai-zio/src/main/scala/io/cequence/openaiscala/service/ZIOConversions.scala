package io.cequence.openaiscala.service

import io.cequence.wsclient.stream.ZioInterop._
import io.cequence.wsclient.stream.{AsyncStream, Bytes}
import zio.stream.ZStream
import zio.{Runtime, Task, Unsafe, ZIO}

import scala.concurrent.{ExecutionContext, Future}

/** The conversions the generated ZIO views use (since 2.0.0). */
private[openaiscala] object ZIOConversions {

  /** A Future-returning call as a lazy Task: the call runs per run of the Task. */
  def task[A](call: ExecutionContext => Future[A]): Task[A] =
    ZIO.fromFuture(call)

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
