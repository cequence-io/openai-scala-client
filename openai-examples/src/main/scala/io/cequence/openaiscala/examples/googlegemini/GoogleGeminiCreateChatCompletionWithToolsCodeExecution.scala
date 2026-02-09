package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.{Content, Part, Tool}
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

/**
 * Example showing code execution with Gemini.
 *
 * The model can generate and execute Python code to solve problems, returning both the code
 * and its execution result.
 *
 * Requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY`
 * environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionWithToolsCodeExecution
    extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  private val tools = Seq(Tool.CodeExecution)

  private val contents: Seq[Content] = Seq(
    Content.textPart(
      "Calculate the first 20 Fibonacci numbers and show them in a formatted list.",
      User
    )
  )

  override protected def run: Future[_] =
    service
      .generateContent(
        contents,
        settings = GenerateContentSettings(
          model = NonOpenAIModelId.gemini_2_5_flash,
          tools = Some(tools),
          generationConfig = Some(
            GenerationConfig(
              maxOutputTokens = Some(2000),
              temperature = Some(0.2)
            )
          )
        )
      )
      .map { response =>
        println("Response (with Code Execution):")

        // Extract and display all parts
        response.candidates.foreach { candidate =>
          candidate.content.parts.foreach {
            case Part.Text(text) =>
              println(s"\nText:\n$text")

            case Part.ExecutableCode(language, code) =>
              println(s"\nExecutable Code ($language):")
              println("```")
              println(code)
              println("```")

            case Part.CodeExecutionResult(outcome, output) =>
              println(s"\nCode Execution Result (outcome: $outcome):")
              output.foreach(out => println(out))

            case _ => // ignore other part types
          }
        }
      }
}
