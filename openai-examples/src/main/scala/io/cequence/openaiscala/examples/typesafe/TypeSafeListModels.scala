package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.typesafe.service.{TypeSafeService, TypeSafeServiceFactory}

import scala.concurrent.Future

/**
 * Lists the models and aliases the account can use - the names `systemOne` takes as `model`.
 *
 * Requires `TYPESAFE_API_KEY`.
 */
object TypeSafeListModels extends ExampleBase[TypeSafeService] {

  override val service: TypeSafeService = TypeSafeServiceFactory()

  override protected def run: Future[_] =
    service.listModels.map { models =>
      models.foreach { model =>
        println(s"${model.name}  (${model.release_date})  ${model.description}")
      }
    }
}
