package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.FileSearchTool
import io.cequence.openaiscala.domain.{AssistantToolResource, ModelId}

import scala.concurrent.Future

object CreateAssistantWithFileSearch extends Example {
  override protected def run: Future[Unit] =
    for {
      assistant <- service.createAssistant(
        model = ModelId.gpt_4o,
        name = Some("Google 10-K Form"),
        description = Some(
          "You are a trustworthy and reliable assistant that helps businesses with their financial reporting."
        ),
        instructions = None,
        tools = Seq(FileSearchTool()),
        toolResources = Some(
          AssistantToolResource(
            AssistantToolResource.FileSearchResources(
              vectorStoreIds = Seq("vs_xxx")
            )
          )
        )
      )
    } yield println(assistant)
}
