package io.cequence.openaiscala.matrix

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.cequence.openaiscala.testkit.StreamRunner
import io.cequence.wsclient.stream.AsyncStream
import io.cequence.wsclient.stream.Fs2Interop._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

/** Runs the specs' streams as `fs2.Stream[IO, _]`s (`-Dopenai.test.consumer=fs2`). */
final class Fs2StreamRunnerProvider extends StreamRunner.Provider {
  override def runner: StreamRunner = Fs2StreamRunner
}

object Fs2StreamRunner extends StreamRunner {

  private val runtime: IORuntime = IORuntime.global

  override val name = "fs2"

  // `toFs2` compiles the stream on the calling thread, so a bridge counter around the call sees
  // every bridge
  override def runToSeq[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[immutable.Seq[A]] = {
    val compiled = stream.toFs2[IO]
    compiled.compile.toList.unsafeToFuture()(runtime)
  }

  override def runDrain[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[Unit] = {
    val compiled = stream.toFs2[IO]
    compiled.compile.drain.unsafeToFuture()(runtime)
  }
}
