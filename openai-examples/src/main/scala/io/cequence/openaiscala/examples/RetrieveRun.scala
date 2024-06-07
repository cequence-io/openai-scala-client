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
//      .retrieveRun("thread_2v2Limjle2axcp1Ye3aBdhtH", "run_UyGjyofIwLfs9GnJUmTBwf3Y")
//      .retrieveRun("thread_QJAdWNGgg2oU7a90S5dv5pxO", "run_zxKcUGmGZo4VQmLbCfDoVA7d")
//      .retrieveRun("thread_kw8vGZ5Gn0fNttA2iNYOgNm9", "run_oIH3FTd10vRmSK3Rsi1r1bhX")
//      .retrieveRun("thread_Kxj1Np8izlEQzxg7TlroziiC", "run_Lul9kHGzEzI1ee8CQREsWkYK")
//      .retrieveRun("thread_vEgr0e5WnoVMjHvxkhGz8WYw", "run_Z9OrPubVLFoQgWH8qEtRr4bg")
//      .retrieveRun("thread_JltAKvPHLqFt2WNgnCgU9ZFJ", "run_aBNmdmR8XzhzohSQlMdp443m")
      .retrieveRun("thread_q5LqRp5snpGIG7yxZvUop2rj", "run_sAPI05LAoUOULAtobHarIaGT")
      .map { maybeRun =>
        println(maybeRun)

        maybeRun.map { run =>
          println(prettyPrint(Json.toJson[Run](run)))
        }

      }
}
