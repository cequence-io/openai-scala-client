package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.settings.CreateCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceConsts

import scala.concurrent.Future

object CreateCompletionJson extends Example with OpenAIServiceConsts {

  private val text =
    """Give me the most populous capital cities in JSON format.
      |[{
      |  "country": "Japan",
      |  "capital": "Tokyo",
      |},{
      |  "country": "China",
      |  "capital": "Beijing",
      |}]
      |
    """.stripMargin

  def run: Future[Unit] =
    service
      .createJsonCompletion(
        text,
        Map(
          "type" -> "array",
          "items" -> Map(
            "type" -> "object",
            "properties" -> Map(
              "country" -> Map(
                "type" -> "string",
                "description" -> "The name of the country"
              ),
              "capital" -> Map(
                "type" -> "string",
                "description" -> "The capital city of the country"
              )
            ),
            "required" -> Seq("country", "capital")
          )
        )
      )
      .map(completion => println(completion.choices.head.text))

}
