package io.cequence.openaiscala.service

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.util.Random

private trait OpenAIMultiServiceAdapter extends OpenAIServiceWrapper {
  protected val underlyings: Seq[OpenAIService]
  protected lazy val count = underlyings.size

  protected def calcIndex: Int

  override protected def wrap[T](
    fun: OpenAIService => Future[T]
  ): Future[T] =
    fun(underlyings(calcIndex))

  override def close =
    underlyings.foreach(_.close())
}

private class OpenAIMultiServiceRotationAdapter(
  val underlyings: Seq[OpenAIService]
) extends OpenAIMultiServiceAdapter {
  private val atomicCounter = new AtomicInteger()

  protected def calcIndex =
    atomicCounter.getAndUpdate(index => (index + 1) % count)
}

private class OpenAIMultiServiceRandomAccessAdapter(
  val underlyings: Seq[OpenAIService]
) extends OpenAIMultiServiceAdapter {
  protected def calcIndex = Random.nextInt(count)
}

/**
 * Load distribution for multiple OpenAIService instances using:
 *  - rotation type (aka round robin)
 *  - random access/order
 */
object OpenAIMultiServiceAdapter {

  // maybe calling it "round robin" would be better?
  def ofRotationType(underlyings: OpenAIService*): OpenAIService =
    new OpenAIMultiServiceRotationAdapter(underlyings)

  // TODO: rename to ofRandomOrder
  def ofRandomAccessType(underlyings: OpenAIService*): OpenAIService =
    new OpenAIMultiServiceRandomAccessAdapter(underlyings)
}