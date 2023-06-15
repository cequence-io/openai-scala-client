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

  override def close() =
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

  @deprecated("Use ofRoundRobinType instead")
  def ofRotationType(underlyings: OpenAIService*): OpenAIService =
    ofRoundRobinType(underlyings:_*)

  @deprecated("Use ofRandomOrderType instead")
  def ofRandomAccessType(underlyings: OpenAIService*): OpenAIService =
    ofRandomOrderType(underlyings:_*)

  def ofRoundRobinType(underlyings: OpenAIService*): OpenAIService =
    new OpenAIMultiServiceRotationAdapter(underlyings)

  def ofRandomOrderType(underlyings: OpenAIService*): OpenAIService =
    new OpenAIMultiServiceRandomAccessAdapter(underlyings)
}