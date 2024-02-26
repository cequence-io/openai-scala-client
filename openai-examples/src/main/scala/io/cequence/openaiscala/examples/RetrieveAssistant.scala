package io.cequence.openaiscala.examples
import scala.concurrent.Future

object RetrieveAssistant extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveAssistant(
        assistantId = "asst_Btbc2h7dqyDU52g1KfBa2XE8"
      )
    } yield {
      println(assistant)
    }

}
