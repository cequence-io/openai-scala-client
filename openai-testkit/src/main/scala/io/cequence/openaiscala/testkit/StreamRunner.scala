package io.cequence.openaiscala.testkit

import io.cequence.wsclient.stream.AsyncStream

import java.util.ServiceLoader
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

/**
 * How a test consumes an [[AsyncStream]]: the neutral interpreter by default, or a native
 * backend (Akka, Pekko, fs2, ZIO) chosen with `-Dopenai.test.consumer=<name>` - the engine
 * matrix runs the same specs under every consumer. A runner COMPILES the stream synchronously
 * in `runToSeq` / `runTake` (so `StreamDiagnostics.counted` around the call sees its bridges)
 * and then runs it.
 */
trait StreamRunner {

  def name: String

  def runToSeq[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[immutable.Seq[A]]

  /** The first `n` elements; the run is cancelled after them. */
  def runTake[A](
    stream: AsyncStream[A],
    n: Int
  )(
    implicit ec: ExecutionContext
  ): Future[immutable.Seq[A]] =
    runToSeq(stream.take(n.toLong))

  /** Runs the stream to its end, keeping no element (for streams too large to collect). */
  def runDrain[A](
    stream: AsyncStream[A]
  )(
    implicit ec: ExecutionContext
  ): Future[Unit]
}

object StreamRunner {

  val ConsumerProperty = "openai.test.consumer"

  /** The bridges every counted spec run must build (`-Dopenai.test.expectedBridges=<n>`). */
  val ExpectedBridgesProperty = "openai.test.expectedBridges"

  lazy val expectedBridges: Option[Long] =
    sys.props.get(ExpectedBridgesProperty).map(_.trim).filter(_.nonEmpty).map(_.toLong)

  /** The neutral interpreter of ws-client-core. */
  object Neutral extends StreamRunner {
    override val name = "neutral"

    override def runToSeq[A](
      stream: AsyncStream[A]
    )(
      implicit ec: ExecutionContext
    ): Future[immutable.Seq[A]] =
      stream.runToSeq

    override def runDrain[A](
      stream: AsyncStream[A]
    )(
      implicit ec: ExecutionContext
    ): Future[Unit] =
      stream.runDrain
  }

  /** Runners other than the neutral one register through `ServiceLoader`. */
  trait Provider {
    def runner: StreamRunner
  }

  lazy val current: StreamRunner =
    sys.props.get(ConsumerProperty).map(_.trim).filter(_.nonEmpty) match {
      case None | Some(Neutral.name) =>
        Neutral

      case Some(wanted) =>
        val it = ServiceLoader.load(classOf[Provider]).iterator()
        var found: Option[StreamRunner] = None
        while (found.isEmpty && it.hasNext) {
          val runner = it.next().runner
          if (runner.name == wanted)
            found = Some(runner)
        }
        found.getOrElse(
          throw new IllegalStateException(
            s"No stream runner '$wanted' on the test classpath ($ConsumerProperty)"
          )
        )
    }
}
