package io.cequence.openaiscala.examples

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency
object CreateCompletionStreamed extends ExampleBase[OpenAIStreamedService] {

  // note the OpenAIStreamedServiceImplicits import allowing to create an OpenAI service with streaming capabilities
  override val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()

  private val text =
    """Extract the name and mailing address from this email:
      |Dear Kelly,
      |It was great to talk to you at the seminar. I thought Jane's talk was quite good.
      |Thank you for the book. Here's my address 2111 Ash Lane, Crestview CA 92002
      |Best,
      |Maya
    """.stripMargin

  override protected def run: Future[_] =
    service
      .createCompletionStreamed(text)
      .runWith(
        Sink.foreach { response =>
          val content = response.choices.headOption.map(_.text).getOrElse("")
          Thread.sleep(100) // to spot how the individual words are being added
          print(content)
        }
      )
}
