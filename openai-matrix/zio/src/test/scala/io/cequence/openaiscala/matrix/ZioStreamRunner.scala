package io.cequence.openaiscala.matrix

import io.cequence.openaiscala.testkit.StreamRunner
import io.cequence.wsclient.stream.AsyncStream
import io.cequence.wsclient.stream.ZioInterop._
import zio.{Runtime, Unsafe}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

/** Runs the specs' streams as `ZStream`s (`-Dopenai.test.consumer=zio`). */
final class ZioStreamRunnerProvider extends StreamRunner.Provider {
  override def runner: StreamRunner = ZioStreamRunner
}

object ZioStreamRunner extends StreamRunner {

  override val name = "zio"

  // `toZStream` compiles the stream on the calling thread, so a bridge counter around the call
  // sees every bridge
  override def runToSeq[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[immutable.Seq[A]] = {
    val compiled = stream.toZStream
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.runToFuture(compiled.runCollect.map(_.toList))
    }
  }

  override def runDrain[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[Unit] = {
    val compiled = stream.toZStream
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.runToFuture(compiled.runDrain)
    }
  }
}
