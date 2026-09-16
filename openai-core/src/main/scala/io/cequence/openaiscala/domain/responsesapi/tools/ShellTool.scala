package io.cequence.openaiscala.domain.responsesapi.tools

/**
 * The hosted shell tool of the Responses API: the model runs commands in a provider-managed
 * container, into which agent skills (`SKILL.md` bundles) can be loaded.
 *
 * @see
 *   <a href="https://developers.openai.com/api/docs/guides/tools-shell">Shell</a>, <a
 *   href="https://developers.openai.com/api/docs/guides/tools-skills">Skills</a>
 */
final case class ShellTool(
  environment: ShellEnvironment = ShellEnvironment.ContainerAuto()
) extends Tool {
  override val `type`: String = "shell"
}

sealed trait ShellEnvironment

object ShellEnvironment {

  /**
   * A container the provider creates for the response.
   *
   * @param skills
   *   skills to load into it
   * @param fileIds
   *   uploaded files to place in it
   */
  final case class ContainerAuto(
    skills: Seq[ShellSkill] = Nil,
    fileIds: Seq[String] = Nil
  ) extends ShellEnvironment

  /** A container created beforehand (`/v1/containers`). */
  final case class ContainerId(id: String) extends ShellEnvironment
}

sealed trait ShellSkill

object ShellSkill {

  /**
   * A skill uploaded to the provider (`/v1/skills`), by id.
   *
   * @param version
   *   a version number or `latest`; the provider's default when unset
   */
  final case class Reference(
    skillId: String,
    version: Option[String] = None
  ) extends ShellSkill

  /** A skill bundle sent along with the request, as a base64-encoded zip. */
  final case class Inline(
    name: String,
    description: String,
    base64Zip: String
  ) extends ShellSkill
}
