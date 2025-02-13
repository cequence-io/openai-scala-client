package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.ModelId

import scala.concurrent.Future

object RetrieveModel extends Example {

  override protected def run: Future[Unit] =
    service.retrieveModel(ModelId.gpt_4_turbo_preview).map(println)
}
