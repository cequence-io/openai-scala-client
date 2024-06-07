package io.cequence.openaiscala.examples

import scala.concurrent.Future
object RetrieveThread extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.retrieveThread(
//        "thread_c6fFMmUw30l30SzG2KdUViMn"
//        "thread_YnAy1CQ51AkBmcI3XWO1zuUB"
//        "thread_QCSvmg9HibS2fFJuUAcGNUBv"
//        "thread_Kxj1Np8izlEQzxg7TlroziiC"
//        "thread_vEgr0e5WnoVMjHvxkhGz8WYw"
        "thread_JltAKvPHLqFt2WNgnCgU9ZFJ"
      )
    } yield {
      println(thread)
    }
}
