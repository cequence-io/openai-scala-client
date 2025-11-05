package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{CreateModelResponseSettings, Inputs}
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.tools.{
  ImageGenerationBackground,
  ImageGenerationTool,
  Tool
}

object CreateModelResponseWithImageGeneration extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Text("Generate an image of a sunset over the ocean with a sailboat."),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_5_mini,
          tools = Seq(
            Tool.imageGeneration(
              background = Some(ImageGenerationBackground.auto),
              model = None,
              quality = Some("high"),
              size = Some("1024x1024"),
              outputFormat = Some("png")
            )
          )
        )
      )
      .map { response =>
        println(s"Response status: ${response.status}")
        println(s"Output text: ${response.outputText.getOrElse("N/A")}")

        // Note: ImageGenerationToolCall is an Input type, not an Output type
        // It would typically appear in subsequent conversation turns or in the response's input items
        println(s"\nImage generation tool was configured with:")
        response.tools.collect { case tool: ImageGenerationTool =>
          println(s"  Background: ${tool.background.map(_.toString).getOrElse("default")}")
          println(s"  Model: ${tool.model.getOrElse("default")}")
          println(s"  Quality: ${tool.quality.getOrElse("auto")}")
          println(s"  Size: ${tool.size.getOrElse("auto")}")
          println(s"  Format: ${tool.outputFormat.getOrElse("png")}")
        }
      }
}
