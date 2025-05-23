package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{Inputs, Input}
import io.cequence.openaiscala.domain.responsesapi.InputMessageContent
import io.cequence.openaiscala.domain.ChatRole
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.domain.SortOrder

object ListModelResponseInputItems extends Example {

  override def run: Future[Unit] =
    for {
      response <- service.createModelResponse(
        Inputs.Items(
          Input.ofInputSystemTextMessage("You are a pirate who likes to rhyme."),
          Input.ofInputMessage(
            Seq(
              InputMessageContent.Text("What is in this image?"),
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

      inputItemsResponse <- service.listModelResponseInputItems(
        response.id,
        order = Some(SortOrder.asc)
      )
    } yield {
      println(s"Response ID    : ${response.id}")
      println(s"Response Text  : ${response.outputText.getOrElse("N/A")}")
      println(
        s"Input Items    :\n${inputItemsResponse.data.map(item => s"${item.`type`} : ${item.toString()}").mkString("\n")}"
      )
    }
}
