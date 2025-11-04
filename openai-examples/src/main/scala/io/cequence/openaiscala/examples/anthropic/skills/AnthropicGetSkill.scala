package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicGetSkill extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val skillId = "docx"

  override protected def run: Future[_] = {
    service
      .getSkill(skillId)
      .map { skill =>
        println(s"Skill Details:")
        println(s"  ID: ${skill.id}")
        println(s"  Type: ${skill.`type`}")
        println(s"  Display Title: ${skill.displayTitle.getOrElse("N/A")}")
        println(s"  Source: ${skill.source}")
        println(s"  Latest Version: ${skill.latestVersion.getOrElse("N/A")}")
        println(s"  Created At: ${skill.createdAt}")
        println(s"  Updated At: ${skill.updatedAt}")
      }
      .recover { case e: Exception =>
        println(s"Error retrieving skill: ${e.getMessage}")
        println(
          s"Make sure to replace the skillId variable with a valid skill ID from your workspace."
        )
        println(s"Run AnthropicListSkills to get available skill IDs.")
      }
  }
}
