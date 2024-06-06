package io.cequence.openaiscala.examples

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.Batch.Batch
import io.cequence.openaiscala.domain.Run
import play.api.libs.json.Json
import play.api.libs.json.Json.prettyPrint

import scala.concurrent.Future

object RetrieveRun extends Example {

  override protected def run: Future[Option[Unit]] =
    service
//      .retrieveRun("thread_GvtoqKcdlD0vTECuuZgcxDfK", "run_RBgA0No2ivbqcNKGSFVCjkxy")
//      .retrieveRun("thread_YnAy1CQ51AkBmcI3XWO1zuUB", "run_teGlp0JtYh1QaFk73FtuHMAz")
//      .retrieveRun("thread_QCSvmg9HibS2fFJuUAcGNUBv", "run_1JXtEreahF4zrqjNssA3doLf")
//      .retrieveRun("thread_7fbLGrV3gNqNrgxbqx5LMq9i", "run_BTVtOS07o7NNpQpvDEAt0LVQ")
      .retrieveRun("thread_2v2Limjle2axcp1Ye3aBdhtH", "run_UyGjyofIwLfs9GnJUmTBwf3Y")
      .map { maybeRun =>
        println(maybeRun)

        maybeRun.map { run =>
          println(prettyPrint(Json.toJson[Run](run)))
        }

      }
}
