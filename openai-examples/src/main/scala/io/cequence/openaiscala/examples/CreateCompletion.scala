package io.cequence.openaiscala.examples

import scala.concurrent.Future
object CreateCompletion extends Example {

  private val text =
    """Extract the name and mailing address from this email:
      |Dear Kelly,
      |It was great to talk to you at the seminar. I thought Jane's talk was quite good.
      |Thank you for the book. Here's my address 2111 Ash Lane, Crestview CA 92002
      |Best,
      |Maya
    """.stripMargin

  def run: Future[Unit] =
    service.createCompletion(text).map(completion => println(completion.choices.head.text))

}
