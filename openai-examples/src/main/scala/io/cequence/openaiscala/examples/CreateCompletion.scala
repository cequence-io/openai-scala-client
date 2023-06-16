package io.cequence.openaiscala.examples

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CreateCompletion extends Example[Unit] {
  val text: String =
    """Extract the name and mailing address from this email:
      |Dear Kelly,
      |It was great to talk to you at the seminar. I thought Jane's talk was quite good.
      |Thank you for the book. Here's my address 2111 Ash Lane, Crestview CA 92002
      |Best,
      |Maya
    """.stripMargin

  def example: Future[Unit] =
    service
      .createCompletion(text)
      .map(completion => println(completion.choices.head.text))

}
