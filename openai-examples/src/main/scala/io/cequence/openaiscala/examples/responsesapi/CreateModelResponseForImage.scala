package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{Inputs, Input}
import io.cequence.openaiscala.domain.responsesapi.InputMessageContent
import io.cequence.openaiscala.domain.ChatRole
import io.cequence.openaiscala.examples.Example

object CreateModelResponseForImage extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Items(
          Input.ofInputMessage(
            Seq(
              InputMessageContent.Text("what is in this image?"),
              InputMessageContent.Image(
                imageUrl = Some(
                  "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg"
                )
              )
            ),
            role = ChatRole.User
          )
        )
      )
      .map { response =>
        println(response.outputText.getOrElse("N/A"))
        response.usage.foreach { usage =>
          println(usage.inputTokens)
          println(usage.outputTokens)
          println(usage.totalTokens)
        }
      }
}
