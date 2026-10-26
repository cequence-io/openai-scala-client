package io.cequence.openaiscala.vertexai.service

/**
 * Vertex AI locations and their API hosts, shared by the chat (SDK) and the batch-prediction
 * (REST) services:
 *   - none or `global` -> `aiplatform.googleapis.com` (requests may be processed anywhere)
 *   - `eu` / `us` (jurisdictional multi-regions) -> `aiplatform.eu.rep.googleapis.com` /
 *     `aiplatform.us.rep.googleapis.com` (ML processing stays within the EU / US)
 *   - a region, e.g. `europe-west4` -> `europe-west4-aiplatform.googleapis.com`
 *
 * See https://docs.cloud.google.com/vertex-ai/generative-ai/docs/learn/locations and
 * https://docs.cloud.google.com/vertex-ai/generative-ai/docs/learn/data-residency.
 */
object VertexAILocation {

  val Global = "global"

  val Jurisdictions: Set[String] = Set("eu", "us")

  /**
   * The location used in the resource path (`projects/{project}/locations/{location}`) - the
   * given one, or `global` when none (or a blank one) is given.
   */
  def resolve(location: Option[String]): String =
    location.map(_.trim).filter(_.nonEmpty).getOrElse(Global)

  /**
   * The API host serving a location. The Vertex AI SDK alone would derive
   * `<location>-aiplatform.googleapis.com` for every location, which does not exist for
   * `global`, `eu` or `us`.
   */
  def apiHost(location: String): String =
    location match {
      case Global => "aiplatform.googleapis.com"
      case jurisdiction if Jurisdictions(jurisdiction) =>
        s"aiplatform.$jurisdiction.rep.googleapis.com"
      case region => s"$region-aiplatform.googleapis.com"
    }
}
