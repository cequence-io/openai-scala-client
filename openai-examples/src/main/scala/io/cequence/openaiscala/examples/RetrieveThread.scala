package io.cequence.openaiscala.examples

import scala.concurrent.Future
object RetrieveThread extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.retrieveThread("thread_xxx")
    } yield {
      println(thread)
    }
}
