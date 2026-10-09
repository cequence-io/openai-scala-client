package io.cequence.openaiscala

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

class FutureHelpersSpec extends AnyWordSpec with Matchers {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private def await[T](future: Future[T]): T = Await.result(future, 10.seconds)

  // runs `f` counting the inputs in flight, the most at once in `maxInFlight`
  private class InFlight {
    private val now = new AtomicInteger(0)
    val maxInFlight = new AtomicInteger(0)

    def apply[T](f: => T): Future[T] = Future {
      // counted outside updateAndGet, whose function may run again under contention
      val current = now.incrementAndGet()
      maxInFlight.updateAndGet(max => math.max(max, current))
      try f
      finally { now.decrementAndGet(); () }
    }
  }

  "parallelize" should {

    "run at most `parallelism` at once, the results in the inputs' order" in {
      val inFlight = new InFlight

      val results = await(FutureHelpers.parallelize(1 to 12, Some(3)) { i =>
        inFlight { Thread.sleep(10L * (i % 4)); i * 10 }
      })

      results shouldBe (1 to 12).map(_ * 10)
      inFlight.maxInFlight.get should be <= 3
    }

    "start the next input as soon as one finishes - a slow input holds back none of the others" in {
      val slow = Promise[Int]()
      val othersDone = new CountDownLatch(8)

      val results = FutureHelpers.parallelize(0 to 8, Some(2)) { i =>
        if (i == 0) slow.future else Future { othersDone.countDown(); i }
      }

      // all the others ran in the second slot while the first one was pending
      othersDone.await(5, TimeUnit.SECONDS) shouldBe true
      slow.success(0)
      await(results) shouldBe (0 to 8)
    }

    "run the inputs one after the other without a parallelism" in {
      val inFlight = new InFlight

      await(FutureHelpers.parallelize(1 to 5, None)(i => inFlight(i))) shouldBe (1 to 5)
      inFlight.maxInFlight.get shouldBe 1
    }

    "fail on a failure, starting no input after it" in {
      val started = new ConcurrentLinkedQueue[Int]()

      val failure = Await.result(
        FutureHelpers
          .parallelize(1 to 5, Some(1)) { i =>
            started.add(i)
            if (i == 2) Future.failed(new IllegalStateException("two"))
            else Future.successful(i)
          }
          .failed,
        10.seconds
      )

      failure.getMessage shouldBe "two"
      started.toArray.toSeq shouldBe Seq(1, 2)
    }

    "take an empty input and fewer inputs than slots, and refuse a parallelism below one" in {
      await(FutureHelpers.parallelize(Seq.empty[Int], Some(4))(Future.successful)) shouldBe Nil
      await(FutureHelpers.parallelize(Seq(1, 2), Some(8))(i => Future.successful(-i))) shouldBe
        Seq(-1, -2)
      an[IllegalArgumentException] should be thrownBy
        FutureHelpers.parallelize(Seq(1), Some(0))(Future.successful)
    }
  }
}
