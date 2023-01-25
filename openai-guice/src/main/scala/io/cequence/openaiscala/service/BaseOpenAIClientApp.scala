package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.slf4j.LoggerFactory

trait BaseOpenAIClientApp extends GuiceContainer with App {

  // modules
  override protected def modules = Seq(
    new ConfigModule(),
    new AkkaModule(),
    new ServiceModule()
  )

  // implicits
  protected implicit val system = instance[ActorSystem]
  protected implicit val materializer = instance[Materializer]
  protected implicit val executionContext = materializer.executionContext
}