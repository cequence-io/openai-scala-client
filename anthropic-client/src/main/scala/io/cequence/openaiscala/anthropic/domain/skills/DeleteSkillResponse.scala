package io.cequence.openaiscala.anthropic.domain.skills

import io.cequence.openaiscala.domain.HasType

/**
 * Response from deleting a skill.
 *
 * @param id
 *   Unique identifier for the skill. The format and length of IDs may change over time.
 * @param `type`
 *   Deleted object type. For Skills, this is always "skill_deleted".
 */
case class DeleteSkillResponse(
  id: String
) extends HasType {
  override val `type`: String = "skill_deleted"
}
