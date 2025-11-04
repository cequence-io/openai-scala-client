package io.cequence.openaiscala.examples.anthropic.skills

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import java.io.File
import java.nio.file.Files
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateSkillVersion extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val skillId = "skill_01..."

  override protected def run: Future[_] = {
    // Create a temporary directory with skill files
    val tempDir = Files.createTempDirectory("example-skill-version").toFile
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
        |description: An updated skill for processing and transforming text
        |---
        |
        |# Text Processing Skill (Version 2)
        |
        |This is an updated version of the text processing skill with enhanced capabilities.
        |
        |## New Features
        |- Case conversion (uppercase, lowercase, title case)
        |- Text reversal
        |- Character counting
        |
        |## Usage
        |Call this skill to perform advanced text processing operations on input text.
        |
        |## Functions
        |- `transform_text(text, mode)`: Converts text based on mode (upper, lower, title)
        |- `reverse_text(text)`: Reverses the input text
        |- `count_chars(text)`: Counts characters in the text
        |""".stripMargin.getBytes
    )

    // Create an updated Python script
    val processPy = new File(tempDir, "process_text.py")
    processPy.deleteOnExit()
    Files.write(
      processPy.toPath,
      """def transform_text(text, mode='upper'):
        |    \"\"\"Transform text based on mode.\"\"\"
        |    if mode == 'upper':
        |        return text.upper()
        |    elif mode == 'lower':
        |        return text.lower()
        |    elif mode == 'title':
        |        return text.title()
        |    else:
        |        return text
        |
        |def reverse_text(text):
        |    \"\"\"Reverse the input text.\"\"\"
        |    return text[::-1]
        |
        |def count_chars(text):
        |    \"\"\"Count characters in the text.\"\"\"
        |    return len(text)
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
      .createSkillVersion(
        skillId = skillId,
        files = files
      )
      .map { skillVersion =>
        println(s"Skill version created successfully:")
        println(s"  ID: ${skillVersion.id}")
        println(s"  Type: ${skillVersion.`type`}")
        println(s"  Skill ID: ${skillVersion.skillId}")
        println(s"  Name: ${skillVersion.name}")
        println(s"  Description: ${skillVersion.description}")
        println(s"  Directory: ${skillVersion.directory}")
        println(s"  Version: ${skillVersion.version}")
        println(s"  Created At: ${skillVersion.createdAt}")

        // Clean up temp files
        files.foreach(_._1.delete())
        tempDir.delete()
      }
  }
}
