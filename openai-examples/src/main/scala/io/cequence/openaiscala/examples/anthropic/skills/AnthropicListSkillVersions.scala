package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicListSkillVersions extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val skillId = "skill_01..."

  override protected def run: Future[_] = {
    println(s"Listing versions for skill: $skillId")
    println()

    service
      .listSkillVersions(
        skillId = skillId,
        page = None,
        limit = Some(20)
      )
      .map { response =>
        println("=" * 60)
        println("SKILL VERSIONS")
        println("=" * 60)
        println(s"Versions found: ${response.data.size}")
        println(s"Has more: ${response.hasMore}")
        println(s"Next page token: ${response.nextPage.getOrElse("N/A")}")
        println()

        response.data.foreach { version =>
          println(s"Version:")
          println(s"  ID: ${version.id}")
          println(s"  Skill ID: ${version.skillId}")
          println(s"  Name: ${version.name}")
          println(s"  Description: ${version.description}")
          println(s"  Directory: ${version.directory}")
          println(s"  Version: ${version.version}")
          println(s"  Created At: ${version.createdAt}")
          println()
        }

        if (response.hasMore && response.nextPage.isDefined) {
          println(
            s"To fetch the next page, use: page = Some(\"${response.nextPage.get}\")"
          )
          println()
        }

        println("=" * 60)
        println("Note: Versions are sorted in reverse chronological order")
        println("(most recent first)")
        println("=" * 60)
      }
      .recover { case e: Exception =>
        println(s"Error listing skill versions: ${e.getMessage}")
        println()
        println("Possible reasons:")
        println("  - The skill ID does not exist")
        println("  - You don't have permission to view this skill")
        println()
        println(
          "Make sure to replace the skillId variable with a valid skill ID."
        )
        println("Run AnthropicListSkills to get available skill IDs.")
      }
  }
}
