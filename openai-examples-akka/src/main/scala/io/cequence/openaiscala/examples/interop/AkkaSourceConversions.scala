package io.cequence.openaiscala.examples.interop

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.RetryHelpers
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.concurrent.ActorSchedulers._
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import io.cequence.wsclient.stream.AsyncStream
import io.cequence.wsclient.stream.SourceConversions._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Existing `Source`-typed Akka code keeps compiling: with `SourceConversions._` imported, a
 * service stream (`AsyncStream`) goes wherever a `Source` is expected. With
 * `ActorSchedulers._` imported, the retry helpers time their back-off on the actor system's
 * scheduler instead of the library's default one.
 *
 * Needs `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object AkkaSourceConversions extends RetryHelpers {

  // stands for Akka code written against 1.x, which took a Source
  private def countWords(
    texts: Source[String, NotUsed]
  )(
    implicit materializer: Materializer
  ): Future[Int] =
    texts
      .runFold("")(_ + _)
      .map(_.split("\\s+").count(_.nonEmpty))(materializer.executionContext)

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("akka-source-conversions")
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 2)

    val engine = PlayWSStreamClientEngine()
    val service = OpenAIServiceFactory.withStreaming.withEngine(engine)

    val texts: AsyncStream[String] = service
      .createChatCompletionStreamed(
        messages = Seq(UserMessage("Describe the Oslofjord in two sentences.")),
        settings = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
      )
      .map(_.contentHead.getOrElse(""))

    // the AsyncStream is passed as a Source (the implicit conversion); a failed run is retried
    val words = countWords(texts).retryOnFailure()

    try
      println(s"Words: ${Await.result(words, 2.minutes)}")
    finally {
      service.close()
      engine.close()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
