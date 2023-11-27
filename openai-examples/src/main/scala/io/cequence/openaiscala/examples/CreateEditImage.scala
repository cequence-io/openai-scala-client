package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings._

object CreateEditImage extends Example {

  private val localOtterImagePath = sys.env("EXAMPLE_OTTER_IMAGE_PATH")
  override protected def run =
    service
      .createImageEdit(
        "A cute baby sea otter wearing a beret",
        image = new java.io.File(localOtterImagePath),
        settings = CreateImageEditSettings(
          model = Some(ModelId.dall_e_2),
          n = Some(1),
          size = Some(ImageSizeType.Small),
          response_format = Some(ImageResponseFormatType.url)
        )
      )
      .map { image =>
        val urls = image.data.flatMap(_.get("url"))
        urls.foreach(println)
      }
}
