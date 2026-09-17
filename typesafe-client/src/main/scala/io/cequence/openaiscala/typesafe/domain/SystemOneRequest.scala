package io.cequence.openaiscala.typesafe.domain

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}

/**
 * The body of `POST /v1/systemone`: named questions, all evaluated against one `state`.
 *
 * @param state
 *   the content the questions refer to - text, or a JSON object / array (a record, a chat log,
 *   your application state); see <a href="https://docs.typesafe.ai/concepts/state">State</a>
 * @param model
 *   a model name or alias from `GET /v1/models`, e.g. [[TypeSafeModelId.jev_latest]]
 * @param questions
 *   questions keyed by a name of your choosing; the answers come back under the same names
 */
final case class SystemOneRequest(
  state: JsValue,
  model: String,
  questions: Map[String, Question]
) {
  require(questions.nonEmpty, "At least one question is required.")
  // anything else (a number, a boolean, null) is a 422 on the API's side
  require(
    state match {
      case _: JsString | _: JsObject | _: JsArray => true
      case _                                      => false
    },
    "The state must be text, a JSON object or a JSON array."
  )
}
