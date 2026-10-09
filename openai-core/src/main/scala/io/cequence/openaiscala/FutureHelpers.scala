package io.cequence.openaiscala

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Bounded parallelism over futures, without a materializer - the futures-only counterpart of
 * Akka Streams' `mapAsync`, without a materializer.
 */
private[openaiscala] object FutureHelpers {

  /**
   * `f` over the inputs, at most `parallelism` running at once - the next input starts as soon
   * as one finishes - with the results in the inputs' order; `None` runs them one after the
   * other. A failure fails the whole, and no input starts after it.
   */
  def parallelize[IN, OUT](
    inputs: Seq[IN],
    parallelism: Option[Int]
  )(
    f: IN => Future[OUT]
  )(
    implicit ec: ExecutionContext
  ): Future[Seq[OUT]] =
    parallelism match {
      case None => seqFutures(inputs)(f)

      case Some(slots) =>
        require(slots > 0, s"parallelism must be positive, got $slots.")
        val indexed = inputs.toIndexedSeq
        val next = new AtomicInteger(0) // the next input to start, shared by the slots

        // a slot: starts the next input when its last one finished, its results kept with
        // their input's index
        def slot(done: Vector[(Int, OUT)]): Future[Vector[(Int, OUT)]] = {
          val index = next.getAndIncrement()
          if (index >= indexed.size) Future.successful(done)
          else
            f(indexed(index)).transformWith {
              case Success(result) => slot(done :+ (index -> result))
              case Failure(e) =>
                next.set(indexed.size) // no input starts after a failure
                Future.failed(e)
            }
        }

        Future
          .traverse(Vector.fill(math.min(slots, indexed.size))(()))(_ => slot(Vector.empty))
          .map(_.flatten.sortBy { case (index, _) => index }.map { case (_, result) =>
            result
          })
    }

  /** `f` over the inputs one after the other, the results in the inputs' order. */
  def seqFutures[IN, OUT](
    inputs: Seq[IN]
  )(
    f: IN => Future[OUT]
  )(
    implicit ec: ExecutionContext
  ): Future[Seq[OUT]] =
    inputs.foldLeft(Future.successful(Vector.empty[OUT])) {
      (
        done,
        input
      ) =>
        done.flatMap(results => f(input).map(results :+ _))
    }
}
