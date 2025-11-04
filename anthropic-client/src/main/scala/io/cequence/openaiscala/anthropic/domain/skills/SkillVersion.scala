package io.cequence.openaiscala.anthropic.domain.skills

import io.cequence.openaiscala.domain.HasType

/**
 * Skill version object returned by the Skills API.
 *
 * @param id
 *   Unique identifier for the skill version. The format and length of IDs may change over
 *   time.
 * @param `type`
 *   Object type. For Skill Versions, this is always "skill_version".
 * @param skillId
 *   Identifier for the skill that this version belongs to.
 * @param name
 *   Human-readable name of the skill version. This is extracted from the SKILL.md file in the
 *   skill upload.
 * @param description
 *   Description of the skill version. This is extracted from the SKILL.md file in the skill
 *   upload.
 * @param directory
 *   Directory name of the skill version. This is the top-level directory name that was
 *   extracted from the uploaded files.
 * @param version
 *   Version identifier for the skill. Each version is identified by a Unix epoch timestamp
 *   (e.g., "1759178010641129").
 * @param createdAt
 *   ISO 8601 timestamp of when the skill version was created.
 */
case class SkillVersion(
  id: String,
  skillId: String,
  name: String,
  description: String,
  directory: String,
  version: String,
  createdAt: String
) extends HasType {
  override val `type`: String = "skill_version"
}
