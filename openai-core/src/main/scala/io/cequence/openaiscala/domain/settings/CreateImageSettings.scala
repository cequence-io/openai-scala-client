package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.EnumValue

case class CreateImageSettings(
    // The number of images to generate. Must be between 1 and 10. Defaults to 1
    n: Option[Int] = None,

    // The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024. Defaults to 1024x1024
    size: Option[ImageSizeType] = None,

    // The format in which the generated images are returned. Must be one of url or b64_json. Defaults to url
    response_format: Option[ImageResponseFormatType] = None,

    // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
    user: Option[String] = None
)

sealed abstract class ImageSizeType(value: String) extends EnumValue(value)

object ImageSizeType {

  case object Small extends ImageSizeType("256x256")
  case object Medium extends ImageSizeType("512x512")
  case object Large extends ImageSizeType("1024x1024")
}

sealed abstract class ImageResponseFormatType extends EnumValue()

object ImageResponseFormatType {
  case object url extends ImageResponseFormatType
  case object b64_json extends ImageResponseFormatType
}
