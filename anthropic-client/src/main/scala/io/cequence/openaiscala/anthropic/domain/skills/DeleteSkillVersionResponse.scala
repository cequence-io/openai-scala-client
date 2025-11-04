package io.cequence.openaiscala.anthropic.domain.skills

import io.cequence.openaiscala.domain.HasType

/**
 * Response from deleting a skill version.
 *
 * @param id
 *   Version identifier for the skill. Each version is identified by a Unix epoch timestamp
 *   (e.g., "1759178010641129").
 * @param `type`
 *   Deleted object type. For Skill Versions, this is always "skill_version_deleted".
 */
case class DeleteSkillVersionResponse(
  id: String
) extends HasType {
  override val `type`: String = "skill_version_deleted"
}
