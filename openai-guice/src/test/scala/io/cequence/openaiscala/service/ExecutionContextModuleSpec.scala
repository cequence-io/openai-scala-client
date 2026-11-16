package io.cequence.openaiscala.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{ExecutionException, TimeUnit}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ExecutionContextModuleSpec extends AnyWordSpec with Matchers {

  // daemon threads that die quietly: the default handler prints a fatal error's stack trace,
  // which CI's log reader reports as a failure
  private def quietPool(threads: Int) =
    new ExecutionContextModule.FixedPool(
      threads,
      (runnable: Runnable) => {
        val thread = new Thread(runnable)
        thread.setDaemon(true)
        thread.setUncaughtExceptionHandler(
          (
            _,
            _
          ) => ()
        )
        thread
      }
    )

  "The module's pool" should {

    "keep the first fatal error a callback throws - the future it was for never completes" in {
      val pool = quietPool(2)
      implicit val ec: ExecutionContext = pool
      try {
        val doomed = Future(1).map[Int](_ => throw new LinkageError("a mismatched jar"))

        val error = Await.result(ExecutionContextModule.fatalError(pool).failed, 10.seconds)
        error shouldBe an[ExecutionException]
        error.getCause shouldBe a[LinkageError]
        error.getCause.getMessage shouldBe "a mismatched jar"
        doomed.isCompleted shouldBe false

        // the pool replaces the thread the error killed
        Await.result(Future(42), 10.seconds) shouldBe 42
      } finally {
        pool.shutdown()
      }
    }

    "leave a non-fatal failure to its future" in {
      val pool = new ExecutionContextModule.FixedPool(2)
      implicit val ec: ExecutionContext = pool
      try {
        val failed =
          Future(1).map[Int](_ => throw new IllegalStateException("a plain failure"))

        Await.result(failed.failed, 10.seconds).getMessage shouldBe "a plain failure"
        ExecutionContextModule.fatalError(pool).isCompleted shouldBe false
      } finally {
        pool.shutdown()
      }
    }

    "shut down through the module, and know no fatal error of another execution context" in {
      val pool = new ExecutionContextModule.FixedPool(1)
      ExecutionContextModule.shutdown(pool)

      pool.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
      ExecutionContextModule.fatalError(ExecutionContext.global).isCompleted shouldBe false
    }
  }
}
