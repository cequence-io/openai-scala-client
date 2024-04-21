package io.cequence.openaiscala.v2.domain.settings

import io.cequence.openaiscala.v2.domain.{EnumValue, NamedEnumValue}

case class CreateImageSettings(
  // The model to use for image generation. Defaults to dall-e-2
  model: Option[String] = None,

  // The number of images to generate. Must be between 1 and 10. For dall-e-3, only n=1 is supported. Defaults to 1
  n: Option[Int] = None,

  // The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024 for dall-e-2.
  // Must be one of 1024x1024, 1792x1024, or 1024x1792 for dall-e-3 models.
  // Defaults to 1024x1024
  size: Option[ImageSizeType] = None,

  // The format in which the generated images are returned. Must be one of url or b64_json. Defaults to url
  response_format: Option[ImageResponseFormatType] = None,

  // The quality of the image that will be generated - standard or hd.
  // hd creates images with finer details and greater consistency across the image. This param is only supported for dall-e-3.
  // Defaults to standard
  quality: Option[ImageQualityType] = None,

  // The style of the generated images. Must be one of vivid or natural.
  // Vivid causes the model to lean towards generating hyper-real and dramatic images.
  // Natural causes the model to produce more natural, less hyper-real looking images.
  // This param is only supported for dall-e-3.
  // Defaults to vivid
  style: Option[ImageStyleType] = None,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None
)

sealed abstract class ImageSizeType(value: String) extends NamedEnumValue(value)

object ImageSizeType {

  case object Small extends ImageSizeType("256x256")
  case object Medium extends ImageSizeType("512x512")
  case object Large extends ImageSizeType("1024x1024")
  case object LargeLandscape extends ImageSizeType("1792x1024")
  case object LargePortrait extends ImageSizeType("1024x1792")

}

sealed trait ImageResponseFormatType extends EnumValue

object ImageResponseFormatType {
  case object url extends ImageResponseFormatType
  case object b64_json extends ImageResponseFormatType
}

sealed trait ImageQualityType extends EnumValue

object ImageQualityType {
  case object standard extends ImageQualityType
  case object hd extends ImageQualityType
}

sealed trait ImageStyleType extends EnumValue

object ImageStyleType {
  case object vivid extends ImageStyleType
  case object natural extends ImageStyleType
}
