package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Batch.Batch
import play.api.libs.json.Json
import io.cequence.openaiscala.JsonFormats._
import play.api.libs.json.Json.prettyPrint
import scala.concurrent.Future

object RetrieveBatch extends Example {

  override protected def run: Future[Option[Unit]] =
//    service.retrieveBatch("batch_zb1dpz3HcFjOdo058gEg8iPn").map { maybeBatch =>
//    service.retrieveBatch("batch_wDuIbjt22f2vpjpn0ulU5xI1").map { maybeBatch =>
    service.retrieveBatch("batch_Ghy5a9EEXDLFqBcJqANpr17F").map { maybeBatch =>
      println(maybeBatch)

      maybeBatch.map { batch =>
        println(prettyPrint(Json.toJson[Batch](batch)))
      }

    }
}
