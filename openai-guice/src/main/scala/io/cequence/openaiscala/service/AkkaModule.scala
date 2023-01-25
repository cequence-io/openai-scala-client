package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{AbstractModule, Injector, Provider}
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule

import javax.inject.Inject
import scala.concurrent.ExecutionContext

object Providers {

  private val name = "main-actor-system"

  class ActorSystemProvider @Inject()(config: Config) extends Provider[ActorSystem] {
    override def get = ActorSystem(name, config)
  }

  class MaterializerProvider @Inject()(system: ActorSystem) extends Provider[Materializer] {
    override def get = Materializer(system)
  }

  class BlockingDispatchedExecutionContextProvider @Inject()(system: ActorSystem) extends Provider[ExecutionContext] {
    override def get: ExecutionContext = system.dispatchers.lookup("blocking-dispatcher")
  }
}

class AkkaModule(includeExecutionContext: Boolean = true) extends AbstractModule with ScalaModule {

  override def configure() {
    bind[ActorSystem].toProvider[Providers.ActorSystemProvider].asEagerSingleton()
    bind[Materializer].toProvider[Providers.MaterializerProvider].asEagerSingleton()

    if (includeExecutionContext) {
      bind[ExecutionContext].toProvider[Providers.BlockingDispatchedExecutionContextProvider].asEagerSingleton()
    }
  }
}
