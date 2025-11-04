package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicDeleteSkillVersion extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val skillId = "skill_01..."
  private val version = "1..."

  override protected def run: Future[_] = {
    println(s"Attempting to delete skill version:")
    println(s"  Skill ID: $skillId")
    println(s"  Version: $version")
    println("WARNING: This will permanently delete the skill version!")
    println()

    service
      .deleteSkillVersion(skillId, version)
      .map { response =>
        println(s"Skill version deleted successfully:")
        println(s"  ID: ${response.id}")
        println(s"  Type: ${response.`type`}")
        println()
        println(s"The skill version has been permanently removed.")
      }
      .recover { case e: Exception =>
        println(s"Error deleting skill version: ${e.getMessage}")
        println()
        println("Possible reasons:")
        println("  - The skill ID does not exist")
        println("  - The version does not exist")
        println(
          "  - The skill is an Anthropic-created skill (only custom skills can be deleted)"
        )
        println("  - You don't have permission to delete this skill version")
        println()
        println(
          "Make sure to replace the skillId and version variables with valid values."
        )
        println(
          "Run AnthropicListSkillVersions to get available custom skill versions."
        )
      }
  }
}
