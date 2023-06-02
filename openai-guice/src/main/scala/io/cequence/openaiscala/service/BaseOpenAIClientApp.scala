package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

trait BaseOpenAIClientApp extends GuiceContainer with App {

  // modules
  override protected def modules = Seq(
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