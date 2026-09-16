package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.typesafe.domain.{ModelMetadata, Question, SystemOneResponse}
import io.cequence.wsclient.service.CloseableService
import play.api.libs.json.{JsString, JsValue}

import scala.concurrent.Future

/**
 * TypeSafe AI's System One API: send a `state` plus named typed questions, get typed answers
 * with calibrated probabilities back - a decision model, not a chat one, so there is no
 * streaming and no `asOpenAI()` adapter.
 *
 * Errors map onto the shared exception hierarchy (`OpenAIScalaUnauthorizedException` for
 * 401/403, `OpenAIScalaRateLimitException` for 429, `OpenAIScalaEngineOverloadedException` for
 * 529 Overloaded, ...), so [[io.cequence.openaiscala.Retryable]] and
 * [[TypeSafeServiceAdapters.retry]] know what to retry.
 *
 * @see
 *   <a href="https://docs.typesafe.ai/api">TypeSafe API reference</a>
 */
trait TypeSafeService extends CloseableService {

  /** The model used when a call names none - `jev-latest` unless configured otherwise. */
  def defaultModel: String

  /**
   * Evaluates every question against the state, in one call. Ask everything you might need at
   * once (questions are evaluated independently and in parallel) and let your code decide what
   * matters.
   *
   * @param state
   *   the content the questions refer to - text, or a JSON object / array
   * @param questions
   *   questions keyed by a name of your choosing (at least one)
   * @param model
   *   a model name or alias from [[listModels]]
   * @return
   *   one [[io.cequence.openaiscala.typesafe.domain.Answer]] per question, under its name
   */
  def systemOne(
    state: JsValue,
    questions: Map[String, Question],
    model: String = defaultModel
  ): Future[SystemOneResponse]

  /** [[systemOne]] over a text state. */
  def systemOne(
    state: String,
    questions: Map[String, Question]
  ): Future[SystemOneResponse] =
    systemOne(JsString(state), questions, defaultModel)

  /** [[systemOne]] over a text state, with an explicit model. */
  def systemOne(
    state: String,
    questions: Map[String, Question],
    model: String
  ): Future[SystemOneResponse] =
    systemOne(JsString(state), questions, model)

  /** The models and aliases available to the account. */
  def listModels: Future[Seq[ModelMetadata]]
}
