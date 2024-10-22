package io.cequence.openaiscala.service.adapter

import io.cequence.wsclient.service.CloseableService

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.util.Random

private trait MultiServiceAdapter[+S <: CloseableService]
    extends ServiceWrapper[S]
    with CloseableService {
  protected val underlyings: Seq[S]
  protected lazy val count = underlyings.size

  protected def calcIndex: Int

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] =
    fun(underlyings(calcIndex))

  override def close(): Unit =
    underlyings.foreach(_.close())
}

private class RoundRobinAdapter[+S <: CloseableService](
  val underlyings: Seq[S]
) extends MultiServiceAdapter[S] {
  private val atomicCounter = new AtomicInteger()

  protected def calcIndex: Int =
    atomicCounter.getAndUpdate(index => (index + 1) % count)
}

private class RandomOrderAdapter[+S <: CloseableService](
  val underlyings: Seq[S]
) extends MultiServiceAdapter[S] {
  protected def calcIndex: Int = Random.nextInt(count)
}