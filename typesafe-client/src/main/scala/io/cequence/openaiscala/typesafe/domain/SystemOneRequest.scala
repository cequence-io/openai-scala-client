package io.cequence.openaiscala.typesafe.domain

import play.api.libs.json.JsValue

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
}
