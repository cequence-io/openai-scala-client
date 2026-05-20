package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{
  BufferedImageHelper,
  ChatCompletionProvider,
  ExampleBase
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import java.io.File
import scala.concurrent.Future

/**
 * Anthropic via AWS Bedrock (OpenAI adapter) with a PDF passed as `FileContent`.
 *
 * Requires `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY`, `AWS_BEDROCK_REGION`, and
 * `EXAMPLE_PDF_PATH`.
 */
object AnthropicBedrockCreateChatCompletionWithFileContentAndPdf
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  private val localPath = sys.env("EXAMPLE_PDF_PATH")
  private val localFile = new File(localPath)

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropicBedrock

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant that summarizes documents."),
    UserSeqMessage(
      Seq(
        TextContent("Summarize this document in 3 bullet points."),
        FileContent(
          fileData = Some(fileBase64DataUrl(localFile)),
          filename = Some(localFile.getName)
        )
      )
    )
  )

  private val modelId = "eu." + NonOpenAIModelId.bedrock_claude_sonnet_4_6

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(modelId, max_tokens = Some(2048))
      )
      .map(response => println(response.contentHead))
}
