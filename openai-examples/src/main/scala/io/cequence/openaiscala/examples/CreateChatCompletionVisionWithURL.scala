package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

object CreateChatCompletionVisionWithURL extends Example {

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserSeqMessage(
      Seq(
        TextContent("What is in this picture?"),
        ImageURLContent(
          "https://upload.wikimedia.org/wikipedia/commons/d/df/Hefeweizen_Glass.jpg"
        )
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_4_vision_preview,
          temperature = Some(0),
          max_tokens = Some(300)
        )
      )
      .map(printMessageContent)
}
