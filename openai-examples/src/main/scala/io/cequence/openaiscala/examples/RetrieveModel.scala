package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.ModelId

object RetrieveModel extends Example {

  override protected def run =
    service.retrieveModel(ModelId.gpt_4_turbo_preview).map(println)
}
