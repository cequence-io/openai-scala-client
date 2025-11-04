package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import java.io.File
import java.nio.file.Files
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateSkill extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  override protected def run: Future[_] = {
    // Create a temporary directory with skill files
    val tempDir = Files.createTempDirectory("example-skill").toFile
    tempDir.deleteOnExit()

    // The skill directory name that will be used in file paths
    // IMPORTANT: This must match the 'name' field in SKILL.md YAML frontmatter
    val skillDirName = "text-processing"

    // Create SKILL.md file (required - must be at top level with YAML frontmatter)
    val skillMd = new File(tempDir, "SKILL.md")
    skillMd.deleteOnExit()
    Files.write(
      skillMd.toPath,
      """---
        |name: text-processing
        |description: A skill for processing and transforming text
        |---
        |
        |# Text Processing Skill
        |
        |This skill demonstrates basic text processing capabilities.
        |
        |## Capabilities
        |- Text transformation (uppercase, lowercase)
        |- String manipulation
        |- Text reversal
        |
        |## Usage
        |Call this skill to perform text processing operations on input text.
        |
        |## Functions
        |- `transform_text(text)`: Converts text to uppercase
        |- `reverse_text(text)`: Reverses the input text
        |""".stripMargin.getBytes
    )

    // Create an example Python script
    val processPy = new File(tempDir, "process_text.py")
    processPy.deleteOnExit()
    Files.write(
      processPy.toPath,
      """def transform_text(text):
        |    \"\"\"Transform text to uppercase.\"\"\"
        |    return text.upper()
        |
        |def reverse_text(text):
        |    \"\"\"Reverse the input text.\"\"\"
        |    return text[::-1]
        |""".stripMargin.getBytes
    )

    // Files must be provided with directory structure in the filename
    // Format: (File, "directory-name/filename")
    // SKILL.md must be at the top level of the directory
    val files = Seq(
      (skillMd, s"$skillDirName/SKILL.md"),
      (processPy, s"$skillDirName/process_text.py")
    )

    service
      .createSkill(
        displayTitle = Some("Example Text Processing Skill"),
        files = files
      )
      .map { skill =>
        println(s"Skill created successfully:")
        println(s"  ID: ${skill.id}")
        println(s"  Type: ${skill.`type`}")
        println(s"  Display Title: ${skill.displayTitle.getOrElse("N/A")}")
        println(s"  Source: ${skill.source}")
        println(s"  Latest Version: ${skill.latestVersion.getOrElse("N/A")}")
        println(s"  Created At: ${skill.createdAt}")
        println(s"  Updated At: ${skill.updatedAt}")

        // Clean up temp files
        files.foreach(_._1.delete())
        tempDir.delete()
      }
  }
}
