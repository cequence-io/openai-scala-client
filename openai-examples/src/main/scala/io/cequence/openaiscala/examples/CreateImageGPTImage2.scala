package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings._

import scala.concurrent.Future

// Smoke test for gpt-image-2.
object CreateImageGPTImage2 extends Example {

  override protected def run: Future[Unit] =
    service
      .createImage(
        "a cute baby sea otter",
        settings = CreateImageSettings(
          model = Some(ModelId.gpt_image_2),
          n = Some(1),
          size = Some(ImageSizeType.Large)
        )
      )
      .map { image =>
        // gpt-image-* returns base64 by default; print whichever field is populated.
        image.data.foreach { entry =>
          entry.get("url").foreach(u => println(s"URL: $u"))
          entry.get("b64_json").foreach { b64 =>
            println(s"b64_json (truncated, ${b64.length} chars): ${b64.take(60)}...")
          }
          entry.get("revised_prompt").foreach(p => println(s"revised_prompt: $p"))
        }
      }
}
