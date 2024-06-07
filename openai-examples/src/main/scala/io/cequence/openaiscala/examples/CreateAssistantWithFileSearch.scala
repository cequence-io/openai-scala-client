package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.{AssistantToolResource, FileSearchSpec, ModelId}
import scala.concurrent.Future

object CreateAssistantWithFileSearch extends Example {
  override protected def run: Future[Unit] =
    for {
      assistant <- service.createAssistant(
        model = ModelId.gpt_4o_2024_05_13,
        name = Some("Google 10-K Form"),
        description = Some(
          "You are a trustworthy and reliable assistant that helps businesses with their financial reporting."
        ),
        instructions = None,
        tools = Seq(FileSearchSpec),
        toolResources = Seq(
          AssistantToolResource.FileSearchResources(
            vectorStoreIds = Seq("vs_xxx")
          )
        )
      )
    } yield println(assistant)
}
