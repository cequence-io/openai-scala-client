package io.cequence.openaiscala.examples.sonar

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.perplexity.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.perplexity.domain.settings.{
  SolarResponseFormat,
  SonarCreateChatCompletionSettings
}
import io.cequence.openaiscala.perplexity.service.{SonarService, SonarServiceFactory}

import scala.concurrent.Future

/**
 * Requires `SONAR_API_KEY` environment variable to be set.
 */
object SonarCreateChatCompletionWithJson extends ExampleBase[SonarService] {

  override val service: SonarService = SonarServiceFactory()

  private val messages = Seq(
    SystemMessage("Be precise and concise."),
    UserMessage("Tell me about Michael Jordan. Please output a JSON object and nothing else.")
  )

  private val modelId = NonOpenAIModelId.sonar

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = SonarCreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(2000),
          response_format = Some(
            SolarResponseFormat.JsonSchema(
              Map(
                "properties" -> Map(
                  "first_name" -> Map("type" -> "string", "title" -> "First Name"),
                  "last_name" -> Map("type" -> "string", "title" -> "Last Name"),
                  "year_of_birth" -> Map("type" -> "integer", "title" -> "Year of Birth"),
                  "num_seasons_in_league" -> Map(
                    "type" -> "integer",
                    "title" -> "Number of Seasons in NBA"
                  )
                ),
                "required" -> Seq(
                  "first_name",
                  "last_name",
                  "year_of_birth",
                  "num_seasons_in_league"
                ),
                "title" -> "AnswerFormat",
                "type" -> "object"
              )
            )
          )
        )
      )
      .map { response =>
        println(response.contentHead)
        println
        println("Citations:\n" + response.citations.mkString("\n"))
      }
}
