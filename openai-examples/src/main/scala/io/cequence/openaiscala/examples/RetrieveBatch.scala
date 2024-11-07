package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Batch.Batch
import play.api.libs.json.Json
import io.cequence.openaiscala.JsonFormats._
import play.api.libs.json.Json.prettyPrint
import scala.concurrent.Future

object RetrieveBatch extends Example {

  override protected def run: Future[Option[Unit]] =
    service.retrieveBatch("batch_xyz").map { maybeBatch =>
      println(maybeBatch)

      maybeBatch.map { batch =>
        println(prettyPrint(Json.toJson[Batch](batch)))
      }

    }
}
