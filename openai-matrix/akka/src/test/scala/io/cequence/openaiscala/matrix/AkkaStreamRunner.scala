package io.cequence.openaiscala.matrix

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import io.cequence.openaiscala.testkit.StreamRunner
import io.cequence.wsclient.stream.AsyncStream
import io.cequence.wsclient.stream.SourceInterop._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

/** Runs the specs' streams as Akka `Source`s (`-Dopenai.test.consumer=akka`). */
final class AkkaStreamRunnerProvider extends StreamRunner.Provider {
  override def runner: StreamRunner = AkkaStreamRunner
}

object AkkaStreamRunner extends StreamRunner {

  // daemonic: the forked test JVM exits without terminating it
  private lazy val materializer: Materializer = Materializer(
    ActorSystem(
      "matrix-akka-consumer",
      ConfigFactory.parseString("akka.daemonic = on").withFallback(ConfigFactory.load())
    )
  )

  override val name = "akka"

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
