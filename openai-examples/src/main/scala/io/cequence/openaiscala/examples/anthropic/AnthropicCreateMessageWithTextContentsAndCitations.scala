package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{MediaBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessageContent}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.{BufferedImageHelper, ExampleBase}

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency
object AnthropicCreateMessageWithTextContentsAndCitations
    extends ExampleBase[AnthropicService]
    with BufferedImageHelper {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val messages: Seq[Message] = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserMessageContent(
      Seq(
        ContentBlockBase(TextBlock("Summarize the document.")),
        MediaBlock.txts(
          Seq(
            "Tokyo,[a] officially the Tokyo Metropolis,[b] is the capital of Japan.",
            """With a population of over 14 million in the city proper in 2023, it is one of the most populous urban areas in the world.
              |The Greater Tokyo Area, which includes Tokyo and parts of six neighboring prefectures, is the most populous metropolitan area in the world,
              |with 41 million residents as of 2024""".stripMargin,
            """Lying at the head of Tokyo Bay, Tokyo is part of the Kantō region, on the central coast of Honshu, Japan's largest island.
              |Tokyo serves as Japan's economic center and the seat of both the Japanese government and the Emperor of Japan.
              |The Tokyo Metropolitan Government administers Tokyo's central 23 special wards, which formerly made up Tokyo City; various commuter towns and suburbs in its western area;
              |and two outlying island chains, the Tokyo Islands.""".stripMargin,
            """Although most of the world recognizes Tokyo as a city, since 1943 its governing structure has been more akin to that of a prefecture,
              |with an accompanying Governor and Assembly taking precedence over the smaller municipal governments that make up the metropolis.
              |Special wards in Tokyo include Chiyoda, the site of the National Diet Building and the Tokyo Imperial Palace; Shinjuku,
              |the city's administrative center; and Shibuya, a hub of commerce and business.""
              |""".stripMargin
          ),
          citations = true
        )
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.claude_3_5_sonnet_20241022,
          max_tokens = 8192
        )
      )
      .map(printTextBlocksWithCitations)

  private def printTextBlocksWithCitations(response: CreateMessageResponse) = {
    val texts = response.textsWithCitations.map { case (text, citations) =>
      val citationsPart =
        if (citations.nonEmpty)
          s"\n{{citations:\n${citations.map(x => s"-${x.citedText}").mkString("\n")}}}"
        else ""
      text + citationsPart
    }

    println(texts.mkString(""))
  }
}
