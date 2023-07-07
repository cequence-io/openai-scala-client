package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.concurrent.ExecutionContext
import net.codingwell.scalaguice.ScalaModule

trait BaseOpenAIClientApp extends GuiceContainer with App {

  // modules
  override protected def modules: Seq[ScalaModule] = Seq(
    new ConfigModule(),
    new AkkaModule(),
    new ServiceModule()
  )

  protected val openAIService = instance[OpenAIService]

  // implicits
  protected implicit val system: ActorSystem = instance[ActorSystem]
  protected implicit val materializer: Materializer = instance[Materializer]
  protected implicit val executionContext: ExecutionContext = materializer.executionContext
}
