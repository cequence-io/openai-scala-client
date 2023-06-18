package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.AbstractModule

import scala.concurrent.ExecutionContext

trait BaseOpenAIClientApp extends GuiceContainer with App {
  protected val openAIService: OpenAIService = instance[OpenAIService]

  // modules
  override protected def modules: Seq[AbstractModule] = Seq(
    new ConfigModule(),
    new AkkaModule(),
    new ServiceModule()
  )

  // implicits
  protected implicit val system: ActorSystem = instance[ActorSystem]
  protected implicit val materializer: Materializer = instance[Materializer]
  protected implicit val executionContext: ExecutionContext =
    materializer.executionContext
}
