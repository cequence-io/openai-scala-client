package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings._

import scala.concurrent.Future

object CreateImageEdit extends Example {

  private val localOtterImagePath = sys.env("EXAMPLE_OTTER_IMAGE_PATH")
  override protected def run: Future[Unit] =
    service
      .createImageEdit(
        "A cute baby sea otter wearing a beret",
        image = new java.io.File(localOtterImagePath),
        settings = CreateImageEditSettings(
          model = Some(ModelId.gpt_image_2),
          n = Some(1),
          size = Some(ImageSizeType.Large)
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
