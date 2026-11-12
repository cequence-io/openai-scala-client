package io.cequence.openaiscala.matrix

import com.typesafe.config.ConfigFactory
import io.cequence.openaiscala.testkit.StreamRunner
import io.cequence.wsclient.stream.AsyncStream
import io.cequence.wsclient.stream.SourceInterop._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

/** Runs the specs' streams as Pekko `Source`s (`-Dopenai.test.consumer=pekko`). */
final class PekkoStreamRunnerProvider extends StreamRunner.Provider {
  override def runner: StreamRunner = PekkoStreamRunner
}

object PekkoStreamRunner extends StreamRunner {

  // daemonic: the forked test JVM exits without terminating it
  private lazy val materializer: Materializer = Materializer(
    ActorSystem(
      "matrix-pekko-consumer",
      ConfigFactory.parseString("pekko.daemonic = on").withFallback(ConfigFactory.load())
    )
  )

  override val name = "pekko"

  // `toSource` compiles the stream on the calling thread, so a bridge counter around the call
  // sees every bridge
  override def runToSeq[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[immutable.Seq[A]] =
    stream.toSource.runWith(Sink.seq)(materializer)

  override def runDrain[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[Unit] =
    stream.toSource.runWith(Sink.ignore)(materializer).map(_ => ())
}
