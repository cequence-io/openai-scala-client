package io.cequence.openaiscala.examples.fireworksai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set
 *
 * Check out the website for more information:
 * https://fireworks.ai/blog/document-inlining-launch
 */
object FireworksAIDocumentInlining extends ExampleBase[OpenAIChatCompletionService] {

  private val fireworksModelPrefix = "accounts/fireworks/models/"
  override val service: OpenAIChatCompletionService = ChatCompletionProvider.fireworks

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserSeqMessage(
      Seq(
        TextContent("What are the candidate's BA and MBA GPAs?"),
        ImageURLContent(
          "https://storage.googleapis.com/fireworks-public/test/sample_resume.pdf#transform=inline"
        )
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + NonOpenAIModelId.llama_v3p3_70b_instruct,
          temperature = Some(0),
          max_tokens = Some(1000)
        )
      )
      .map(printMessageContent)
}
