package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings._

import scala.concurrent.Future

object CreateImage extends Example {

  override protected def run: Future[Unit] =
    service
      .createImage(
        "a cute baby sea otter",
        settings = CreateImageSettings(
          model = Some(ModelId.gpt_image_2),
          n = Some(1),
          size = Some(ImageSizeType.Large),
          quality = Some(ImageQualityType.medium)
        )
      )
      .map { image =>
        // gpt-image models always return base64 (no URLs)
        image.data.flatMap(_.get("b64_json")).foreach { b64 =>
          val file = java.io.File.createTempFile("image", ".png")
          java.nio.file.Files.write(file.toPath, java.util.Base64.getDecoder.decode(b64))
          println(s"Saved to ${file.getAbsolutePath}")
        }
      }
}
