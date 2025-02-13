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
          model = Some(ModelId.dall_e_3),
          n = Some(1),
          size = Some(ImageSizeType.Large),
          style = Some(ImageStyleType.natural),
          quality = Some(ImageQualityType.hd),
          response_format = Some(ImageResponseFormatType.url)
        )
      )
      .map { image =>
        val urls = image.data.flatMap(_.get("url"))
        urls.foreach(println)
      }
}
