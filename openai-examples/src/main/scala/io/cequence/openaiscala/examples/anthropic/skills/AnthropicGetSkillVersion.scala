package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicGetSkillVersion extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val skillId = "skill_01..."
  private val version = "1.."

  override protected def run: Future[_] = {
    println(s"Retrieving skill version:")
    println(s"  Skill ID: $skillId")
    println(s"  Version: $version")
    println()

    service
      .getSkillVersion(skillId, version)
      .map { skillVersion =>
        println("=" * 60)
        println("SKILL VERSION DETAILS")
        println("=" * 60)
        println(s"ID: ${skillVersion.id}")
        println(s"Type: ${skillVersion.`type`}")
        println(s"Skill ID: ${skillVersion.skillId}")
        println(s"Name: ${skillVersion.name}")
        println(s"Description: ${skillVersion.description}")
        println(s"Directory: ${skillVersion.directory}")
        println(s"Version: ${skillVersion.version}")
        println(s"Created At: ${skillVersion.createdAt}")
        println("=" * 60)
      }
      .recover { case e: Exception =>
        println(s"Error retrieving skill version: ${e.getMessage}")
        println()
        println("Possible reasons:")
        println("  - The skill ID does not exist")
        println("  - The version does not exist")
        println("  - You don't have permission to view this skill")
        println()
        println(
          "Make sure to replace the skillId and version variables with valid values."
        )
        println("Run AnthropicListSkillVersions to get available versions.")
      }
  }
}
