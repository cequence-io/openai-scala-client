package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.typesafe.domain._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * A decision service for tests: answers every call with `answers` (as `jev-1.13.0`, request
 * `req-1`) and records the last request.
 */
class RecordingTypeSafeService(
  answers: Map[String, Answer],
  usage: Usage = Usage(Some(300), Some(40))
) extends TypeSafeService {

  @volatile var lastState: Option[JsValue] = None
  @volatile var lastQuestions: Map[String, Question] = Map.empty
  @volatile var lastModel: Option[String] = None

  override val defaultModel = "jev-latest"

  override def systemOne(
    state: JsValue,
    questions: Map[String, Question],
    model: String
  ): Future[SystemOneResponse] = {
    lastState = Some(state)
    lastQuestions = questions
    lastModel = Some(model)
    Future.successful(SystemOneResponse("jev-1.13.0", answers, usage, Some("req-1")))
  }

  override def listModels: Future[Seq[ModelMetadata]] = Future.successful(Nil)

  override def close(): Unit = ()
}
