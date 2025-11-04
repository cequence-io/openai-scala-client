package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicDeleteSkill extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val skillId = "skill_01..."

  override protected def run: Future[_] = {
    println(s"Attempting to delete skill: $skillId")
    println("WARNING: This will permanently delete the skill!")
    println()

    service
      .deleteSkill(skillId)
      .map { response =>
        println(s"Skill deleted successfully:")
        println(s"  ID: ${response.id}")
        println(s"  Type: ${response.`type`}")
        println()
        println(s"The skill has been permanently removed from your workspace.")
      }
      .recover { case e: Exception =>
        println(s"Error deleting skill: ${e.getMessage}")
        println()
        println("Possible reasons:")
        println("  - The skill ID does not exist")
        println(
          "  - The skill is an Anthropic-created skill (only custom skills can be deleted)"
        )
        println("  - You don't have permission to delete this skill")
        println()
        println("Make sure to replace the skillId variable with a valid custom skill ID.")
        println(
          "Run AnthropicListSkills with source=SkillSource.custom to get custom skill IDs."
        )
      }
  }
}
