package io.cequence.openaiscala.anthropic.domain.skills

import io.cequence.openaiscala.domain.HasType

/**
 * Skill object returned by the Skills API.
 *
 * @param id
 *   Unique identifier for the skill. The format and length of IDs may change over time.
 * @param `type`
 *   Object type. For Skills, this is always "skill".
 * @param displayTitle
 *   Display title for the skill. This is a human-readable label that is not included in the
 *   prompt sent to the model.
 * @param source
 *   Source of the skill. Either "custom" (created by a user) or "anthropic" (created by
 *   Anthropic).
 * @param latestVersion
 *   The latest version identifier for the skill. This represents the most recent version of
 *   the skill that has been created.
 * @param createdAt
 *   ISO 8601 timestamp of when the skill was created.
 * @param updatedAt
 *   ISO 8601 timestamp of when the skill was last updated.
 */
case class Skill(
  id: String,
  displayTitle: Option[String] = None,
  source: String,
  latestVersion: Option[String] = None,
  createdAt: String,
  updatedAt: String
) extends HasType {
  override val `type`: String = "skill"
}
