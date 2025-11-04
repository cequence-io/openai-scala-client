package io.cequence.openaiscala.anthropic.domain.skills

import io.cequence.wsclient.domain.EnumValue

/**
 * Container for skills to be loaded and reused across requests.
 *
 * @param id
 *   Container id for reuse across requests.
 * @param skills
 *   List of skills to load in the container (maximum 8).
 */
case class Container(
  id: Option[String] = None,
  skills: Seq[SkillParams] = Nil
)

/**
 * Skill source type.
 */
sealed trait SkillSource extends EnumValue

object SkillSource {
  case object custom extends SkillSource
  case object anthropic extends SkillSource

  def values: Seq[SkillSource] = Seq(custom, anthropic)
}

/**
 * Parameters for a skill to be loaded in a container.
 *
 * @param skillId
 *   Skill ID (length 1-64 characters).
 * @param `type`
 *   Type of skill - either 'anthropic' (built-in) or 'custom' (user-defined).
 * @param version
 *   Skill version or 'latest' for most recent version (length 1-64 characters).
 */
case class SkillParams(
  skillId: String,
  `type`: SkillSource,
  version: Option[String] = None
)
