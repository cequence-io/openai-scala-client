package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ApproximateLocation,
  SearchContextSize,
  UserLocation,
  WebSearchOptions
}

import scala.concurrent.Future

object CreateChatCompletionWithWebSearch extends Example {

  private val messages = Seq(
    SystemMessage("You are a helpful weather assistant."),
    UserMessage("When was the last tornado in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatWebSearchCompletion(
        messages = messages,
        searchOptions = WebSearchOptions(
          user_location = Some(
            UserLocation(
              approximate = ApproximateLocation(
                country = "NO",
                city = "Oslo",
                region = "Oslo"
              )
            )
          ),
          search_context_size = Some(SearchContextSize.high)
        )
      )
      .map { response =>
        println(response.usage.get)
        println(response.contentHead)
        println(response.annotationsHead.mkString("\n"))
      }
}
