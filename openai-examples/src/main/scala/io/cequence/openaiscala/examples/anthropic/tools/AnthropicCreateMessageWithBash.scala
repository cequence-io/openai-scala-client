package io.cequence.openaiscala.examples.anthropic.tools

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.Tool
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future
import scala.sys.process._

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageWithBash extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val model = NonOpenAIModelId.claude_sonnet_4_5_20250929

  val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("Create a CSV file with sample sales data for 5 products.")
  )

  override protected def run: Future[_] =
    for {
      response <- service.createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = model,
          max_tokens = 4096,
          tools = Seq(Tool.bash())
        )
      )

      command = {
        val toolUseBlock = response.toolUseBlocks.headOption.getOrElse(
          throw new RuntimeException("No tool use block found in the response")
        )

        assert(toolUseBlock.name == "bash")

        (toolUseBlock.input \ "command")
          .asOpt[String]
          .getOrElse(
            throw new RuntimeException(
              "No command found in the tool use block input : " + toolUseBlock.input
            )
          )
      }

      result = {
        println("=" * 60)
        println("Executing command (potentially unsafe):")
        println(command)
        println("=" * 60)
        println()

        try {
          val output = Seq("bash", "-c", command).!!
          println("Command output:")
          println(output)
          output
        } catch {
          case ex: Exception =>
            val errorMsg = s"Error executing command: ${ex.getMessage}"
            println(errorMsg)
            errorMsg
        }
      }
    } yield {
      response.blockContents.zipWithIndex.foreach { case (blockContent, index) =>
        println(s"Block ${index + 1}:")
        println(blockContent)
        println("=" * 60)
      }

      println()
      println("=" * 60)
      println("Final Result:")
      println(result)
      println("=" * 60)
    }
}
