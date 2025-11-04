package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.domain.skills.SkillSource
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicListSkills extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  override protected def run: Future[_] = {
    for {
      // First, list Anthropic-created skills
      anthropicSkills <- service.listSkills(
        page = None,
        limit = Some(20),
        source = Some(SkillSource.anthropic)
      )

      // Then, list custom (user-created) skills
      customSkills <- service.listSkills(
        page = None,
        limit = Some(20),
        source = Some(SkillSource.custom)
      )
    } yield {
      // Display Anthropic skills
      println("=" * 60)
      println("ANTHROPIC SKILLS")
      println("=" * 60)
      println(s"Skills found: ${anthropicSkills.data.size}")
      println(s"Has more: ${anthropicSkills.hasMore}")
      println(s"Next page token: ${anthropicSkills.nextPage.getOrElse("N/A")}")
      println()

      anthropicSkills.data.foreach { skill =>
        println(s"Skill:")
        println(s"  ID: ${skill.id}")
        println(s"  Display Title: ${skill.displayTitle.getOrElse("N/A")}")
        println(s"  Source: ${skill.source}")
        println(s"  Latest Version: ${skill.latestVersion.getOrElse("N/A")}")
        println(s"  Created At: ${skill.createdAt}")
        println(s"  Updated At: ${skill.updatedAt}")
        println()
      }

      if (anthropicSkills.hasMore && anthropicSkills.nextPage.isDefined) {
        println(
          s"To fetch the next page, use: page = Some(${anthropicSkills.nextPage.get})"
        )
        println()
      }

      // Display custom skills
      println("=" * 60)
      println("CUSTOM SKILLS")
      println("=" * 60)
      println(s"Skills found: ${customSkills.data.size}")
      println(s"Has more: ${customSkills.hasMore}")
      println(s"Next page token: ${customSkills.nextPage.getOrElse("N/A")}")
      println()

      customSkills.data.foreach { skill =>
        println(s"Skill:")
        println(s"  ID: ${skill.id}")
        println(s"  Display Title: ${skill.displayTitle.getOrElse("N/A")}")
        println(s"  Source: ${skill.source}")
        println(s"  Latest Version: ${skill.latestVersion.getOrElse("N/A")}")
        println(s"  Created At: ${skill.createdAt}")
        println(s"  Updated At: ${skill.updatedAt}")
        println()
      }

      if (customSkills.hasMore && customSkills.nextPage.isDefined) {
        println(s"To fetch the next page, use: page = Some(${customSkills.nextPage.get})")
        println()
      }

      // Summary
      println("=" * 60)
      println("SUMMARY")
      println("=" * 60)
      println(s"Total Anthropic Skills: ${anthropicSkills.data.size}")
      println(s"Total Custom Skills: ${customSkills.data.size}")
      println(s"Total Skills: ${anthropicSkills.data.size + customSkills.data.size}")
    }
  }
}
