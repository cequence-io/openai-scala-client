package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.{Content, Tool}
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

/**
 * Example showing Google Search grounding with Gemini.
 *
 * The model will use Google Search to find up-to-date information to answer queries.
 *
 * Requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY`
 * environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionWithToolsGoogleSearch
    extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  private val tools = Seq(Tool.GoogleSearch)

  private val contents: Seq[Content] = Seq(
    Content.textPart("What are the latest news about AI developments today?", User)
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
              temperature = Some(0.7)
            )
          )
        )
      )
      .map { response =>
        println("Response (with Google Search grounding):")
        println(response.contentHeadText)

        // Check for grounding metadata if available
        response.candidates.foreach { candidate =>
          candidate.groundingMetadata.foreach { metadata =>
            println("\nGrounding metadata:")
            metadata.searchEntryPoint.foreach { entry =>
              println(s"  Search entry point: ${entry.renderedContent.getOrElse("N/A")}")
            }
            if (metadata.groundingChunks.nonEmpty) {
              println("  Grounding chunks:")
              metadata.groundingChunks.foreach { chunk =>
                println(s"    - ${chunk.web.title}: ${chunk.web.uri}")
              }
            }
            if (metadata.webSearchQueries.nonEmpty) {
              println(s"  Web search queries: ${metadata.webSearchQueries.mkString(", ")}")
            }
          }
        }
      }
}
