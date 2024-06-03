package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateEditSettings
import scala.concurrent.Future

object CreateEdit extends Example {

  override protected def run: Future[Unit] =
    service
      .createEdit(
        input = "What day of the wek is it?",
        instruction = "Fix the spelling mistakes",
        settings = CreateEditSettings(
          model = ModelId.text_davinci_edit_001,
          temperature = Some(0.9)
        )
      )
      .map { edit =>
        println(edit.choices.head.text)
      }
}
