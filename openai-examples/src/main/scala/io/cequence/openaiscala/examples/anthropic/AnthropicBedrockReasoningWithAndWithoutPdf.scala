package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.examples.{
  BufferedImageHelper,
  ChatCompletionProvider,
  ExampleBase
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import java.io.File
import scala.concurrent.Future

/**
 * Verifies that `reasoning_effort` is honored by the Anthropic Bedrock adapter for both:
 *   1. a pure text question 2. a question with a PDF passed as `FileContent`
 *
 * Evidence: `completion_tokens_details.reasoning_tokens` should be > 0 in both runs.
 *
 * Requires `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY`, `AWS_BEDROCK_REGION`, and
 * `EXAMPLE_PDF_PATH`.
 */
object AnthropicBedrockReasoningWithAndWithoutPdf
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  private val localPdfPath = sys.env("EXAMPLE_PDF_PATH")
  private val pdfBase64 = pdfBase64Source(new File(localPdfPath))

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropicBedrock

  private val modelId = "eu." + NonOpenAIModelId.bedrock_claude_sonnet_4_6

  private val settings = CreateChatCompletionSettings(
    modelId,
    reasoning_effort = Some(ReasoningEffort.high),
    max_tokens = Some(8000)
  )

  private val textMessages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a careful problem solver."),
    UserMessage(
      "A bat and a ball cost 1.10 in total. The bat costs 1.00 more than the ball. " +
        "How much does the ball cost? Think step by step, then give the final answer."
    )
  )

  private val pdfMessages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a careful problem solver."),
    UserSeqMessage(
      Seq(
        TextContent(
          "Look carefully at the attached invoice/inspection. " +
            "Compute the VAT-only portion of the invoice total (i.e. the amount of VAT " +
            "included in 8 530,50 CZK if VAT is 21%). Show your reasoning and give the final number."
        ),
        FileContent(
          fileData = Some(s"data:application/pdf;base64,$pdfBase64"),
          filename = Some(new File(localPdfPath).getName)
        )
      )
    )
  )

  private def label(s: String): String = s"\n===== $s =====\n"

  override protected def run: Future[_] =
    for {
      _ <- {
        println(label("PURE TEXT + reasoning_effort=high"))
        service.createChatCompletion(textMessages, settings).map { r =>
          val reasoningTokens =
            r.usage.flatMap(_.completion_tokens_details.flatMap(_.reasoning_tokens))
          println(r.contentHead)
          println(
            s"\n[usage] $reasoningTokens reasoning_tokens; total=${r.usage.map(_.total_tokens)}"
          )
        }
      }
      _ <- {
        println(label("PDF + reasoning_effort=high"))
        service.createChatCompletion(pdfMessages, settings).map { r =>
          val reasoningTokens =
            r.usage.flatMap(_.completion_tokens_details.flatMap(_.reasoning_tokens))
          println(r.contentHead)
          println(
            s"\n[usage] $reasoningTokens reasoning_tokens; total=${r.usage.map(_.total_tokens)}"
          )
        }
      }
    } yield ()
}
