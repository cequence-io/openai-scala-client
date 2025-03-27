package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{Inputs, CreateModelResponseSettings}
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.OutputMessageContent.OutputText
import io.cequence.openaiscala.domain.responsesapi.tools.WebSearchTool
import io.cequence.openaiscala.domain.responsesapi.Annotation

object CreateModelResponseWithWebSearch extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Text("What was a positive news story from today?"),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_4o_2024_08_06,
          tools = Seq(WebSearchTool())
        )
      )
      .map { response =>
        println(response.outputText.getOrElse("N/A"))

        // citations
        val citations: Seq[Annotation.UrlCitation] = response.outputMessageContents.collect {
          case e: OutputText =>
            e.annotations.collect { case citation: Annotation.UrlCitation => citation }
        }.flatten

        println("Citations:")
        citations.foreach { citation =>
          println(s"${citation.title} - ${citation.url}")
        }
      }
}
