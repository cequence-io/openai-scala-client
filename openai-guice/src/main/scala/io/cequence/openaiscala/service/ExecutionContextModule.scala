package io.cequence.openaiscala.service

import com.google.inject.{AbstractModule, Provider}
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

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

      ExecutionContext.fromExecutorService(
        Executors.newFixedThreadPool(threads, threadFactory)
      )
    }
  }

  private[service] def shutdown(ec: ExecutionContext): Unit =
    ec match {
      case service: ExecutionContextExecutorService => service.shutdown()
      case _                                        => ()
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
