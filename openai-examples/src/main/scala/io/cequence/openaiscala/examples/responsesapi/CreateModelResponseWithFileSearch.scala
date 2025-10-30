package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{Inputs, CreateModelResponseSettings}
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.tools.FileSearchTool
import io.cequence.openaiscala.domain.responsesapi.Annotation
import io.cequence.openaiscala.domain.responsesapi.OutputMessageContent.OutputText

object CreateModelResponseWithFileSearch extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Text("What are the attributes of an ancient brown dragon?"),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_5_mini,
          tools = Seq(
            FileSearchTool(
              vectorStoreIds = Seq("vs_1234567890"),
              maxNumResults = Some(20),
              filters = None,
              rankingOptions = None
            )
          )
        )
      )
      .map { response =>
        println(response.outputText.getOrElse("N/A"))

        // citations
        val citations: Seq[Annotation.FileCitation] = response.outputMessageContents.collect {
          case e: OutputText =>
            e.annotations.collect { case citation: Annotation.FileCitation => citation }
        }.flatten

        println("Citations:")
        citations.foreach { citation =>
          println(s"${citation.fileId} - ${citation.filename}")
        }
      }
}
