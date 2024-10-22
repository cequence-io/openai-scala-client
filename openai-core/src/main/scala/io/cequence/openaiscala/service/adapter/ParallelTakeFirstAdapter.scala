package io.cequence.openaiscala.service.adapter

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.wsclient.service.CloseableService
import org.slf4j.LoggerFactory

import scala.concurrent.Future

private class ParallelTakeFirstAdapter[+S <: CloseableService](
  underlyings: Seq[S]
)(
  implicit materializer: Materializer
) extends ServiceWrapper[S]
    with CloseableService {

  private val logger = LoggerFactory.getLogger(getClass)

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] = {
    logger.debug(s"Running parallel/redundant processing with ${underlyings.size} services.")

    val sources = Source
      .fromIterator(() => underlyings.toIterator)
      .mapAsyncUnordered(underlyings.size)(fun)

    sources.runWith(Sink.head)
  }

  override def close(): Unit =
    underlyings.foreach(_.close())
}
