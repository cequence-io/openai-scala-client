package io.cequence.openaiscala.examples

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.Run
import play.api.libs.json.Json
import play.api.libs.json.Json.prettyPrint

import scala.concurrent.Future

object RetrieveRun extends Example {

  override protected def run: Future[Option[Unit]] =
    service
      .retrieveRun("thread_VStezPO5449TyPgpkgeEtI81", "run_g1d7aSLHwDX14m9CU0RZs3Tg")
      .map { maybeRun =>
        println(maybeRun)

        maybeRun.map { run =>
          println(prettyPrint(Json.toJson[Run](run)))
        }

      }
}
