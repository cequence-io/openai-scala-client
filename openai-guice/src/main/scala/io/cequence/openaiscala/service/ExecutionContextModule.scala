package io.cequence.openaiscala.service

import com.google.inject.{AbstractModule, Provider}
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule

import java.util.concurrent.{
  AbstractExecutorService,
  ExecutionException,
  LinkedBlockingQueue,
  ThreadFactory,
  ThreadPoolExecutor,
  TimeUnit
}
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future, Promise}
import scala.util.control.NonFatal

object ExecutionContextModule {

  /** The config key of the pool's size. */
  val ThreadsConfigPath: String = "openai-scala-client.guice.threads"

  val DefaultThreads: Int = 40

  private val threadFactory: ThreadFactory = new ThreadFactory {
    private val counter = new AtomicInteger(0)

    override def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable, s"openai-scala-guice-${counter.incrementAndGet()}")
      // daemon threads: a container that is never terminated cannot block the JVM's exit
      thread.setDaemon(true)
      thread
    }
  }

  /**
   * A fixed pool of daemon threads, sized by `openai-scala-client.guice.threads` (40 when
   * unset). `GuiceContainer.terminate()` shuts it down.
   */
  final class FixedPoolExecutionContextProvider @Inject() (config: Config)
      extends Provider[ExecutionContext] {

    override def get: ExecutionContext = {
      val threads =
        if (config.hasPath(ThreadsConfigPath))
          config.getInt(ThreadsConfigPath)
        else
          DefaultThreads

      new FixedPool(threads)
    }
  }

  /**
   * The pool, which also keeps the first fatal error (a `LinkageError` from a mismatched jar,
   * an `OutOfMemoryError`) one of its tasks threw: such an error kills its thread and never
   * completes the future whose callback threw it - the promise rethrows it - so whoever waits
   * for that future (`closeAndExit()`) waits for `fatalError` too.
   */
  private[service] final class FixedPool(threads: Int)
      extends AbstractExecutorService
      with ExecutionContextExecutorService {

    private val fatal = Promise[Nothing]()

    private val pool = new ThreadPoolExecutor(
      threads,
      threads,
      0L,
      TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue[Runnable](),
      threadFactory
    ) {
      override protected def afterExecute(
        task: Runnable,
        error: Throwable
      ): Unit =
        Option(error).filterNot(NonFatal(_)).foreach { error =>
          fatal.tryFailure(
            new ExecutionException("A task of the pool failed with a fatal error", error)
          )
        }
    }

    /** Fails with the first fatal error a task threw, if one does. */
    def fatalError: Future[Nothing] = fatal.future

    override def execute(task: Runnable): Unit = pool.execute(task)

    override def reportFailure(cause: Throwable): Unit =
      ExecutionContext.defaultReporter(cause)

    override def shutdown(): Unit = pool.shutdown()

    override def shutdownNow(): java.util.List[Runnable] = pool.shutdownNow()

    override def isShutdown: Boolean = pool.isShutdown

    override def isTerminated: Boolean = pool.isTerminated

    override def awaitTermination(
      timeout: Long,
      unit: TimeUnit
    ): Boolean = pool.awaitTermination(timeout, unit)
  }

  private[service] def shutdown(ec: ExecutionContext): Unit =
    ec match {
      case service: ExecutionContextExecutorService => service.shutdown()
      case _                                        => ()
    }

  // the first fatal error of the module's pool; never, for an execution context of another kind
  private[service] def fatalError(ec: ExecutionContext): Future[Nothing] =
    ec match {
      case pool: FixedPool => pool.fatalError
      case _               => Future.never
    }
}

/**
 * Binds the `ExecutionContext` the services run their callbacks on. The services need no actor
 * system or materializer: each owns (or shares) a ws-client engine.
 */
class ExecutionContextModule extends AbstractModule with ScalaModule {

  override def configure: Unit =
    bind[ExecutionContext]
      .toProvider[ExecutionContextModule.FixedPoolExecutionContextProvider]
      .asEagerSingleton()
}
