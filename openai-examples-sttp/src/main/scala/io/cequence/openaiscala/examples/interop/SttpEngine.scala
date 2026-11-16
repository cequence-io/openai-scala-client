package io.cequence.openaiscala.examples.interop

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.spi.WSClientEngineRegistry
import io.cequence.wsclient.service.ws.SttpWSClientEngine
import sttp.client4.Backend
import sttp.client4.httpclient.HttpClientFutureBackend

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * The calls on an sttp `Future` backend of your choice - here sttp's own JDK `HttpClient`
 * backend; OkHttp, Armeria and the other sttp `Future` backends plug in the same way. sttp
 * does not stream, so a streamed service runs on the highest-priority engine that does -
 * `jdk`, which comes with the library.
 *
 * Needs `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object SttpEngine {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val backend: Backend[Future] = HttpClientFutureBackend()

    val engine = SttpWSClientEngine() // timeouts and proxy are the backend's
    val service = OpenAIServiceFactory.withEngine(engine)

    // streams: the jdk engine (sttp is not eligible for streaming)
    val streamingEngine = WSClientEngineRegistry.outputStreamed()
    val streamedService = OpenAIServiceFactory.withStreaming.withEngine(streamingEngine)

    val messages = Seq(UserMessage("Name three Norwegian fjords."))
    val settings = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)

    try {
      val answer = Await.result(service.createChatCompletion(messages, settings), 2.minutes)
      println(s"sttp: ${answer.contentHead}")

      print("jdk stream: ")
      Await.result(
        streamedService
          .createChatCompletionStreamed(messages, settings)
          .runForeach(chunk => print(chunk.contentHead.getOrElse(""))),
        2.minutes
      )
      println()
    } finally {
      service.close()
      streamedService.close()
      streamingEngine.close()
      engine.close() // closes the backend too, also one passed in
    }
  }
}
