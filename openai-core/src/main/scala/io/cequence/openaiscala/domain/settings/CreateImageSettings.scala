package io.cequence.openaiscala.domain.settings

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

case class CreateImageSettings(
  // The model to use for image generation (required - dall-e-2/3 are shut down), e.g. gpt-image-2
  model: Option[String] = None,

  // The number of images to generate. Must be between 1 and 10. Defaults to 1
  n: Option[Int] = None,

  // The size of the generated images: Large (1024x1024), Landscape (1536x1024), Portrait (1024x1536) or Auto
  // for gpt-image models (the other sizes were dall-e-only). Defaults to auto
  size: Option[ImageSizeType] = None,

  // dall-e only (shut down): gpt-image models reject it and always return b64_json
  response_format: Option[ImageResponseFormatType] = None,

  // The quality of the image: low, medium, high or auto for gpt-image models (standard / hd were dall-e-3 only).
  // Defaults to auto
  quality: Option[ImageQualityType] = None,

  // dall-e-3 only (shut down): vivid or natural - gpt-image models reject it
  style: Option[ImageStyleType] = None,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None
)

sealed abstract class ImageSizeType(value: String) extends NamedEnumValue(value)

object ImageSizeType {

  case object Large extends ImageSizeType("1024x1024")
  case object Landscape extends ImageSizeType("1536x1024")
  case object Portrait extends ImageSizeType("1024x1536")
  case object Auto extends ImageSizeType("auto")

  @deprecated("dall-e-2 / dall-e-3 only - both shut down; gpt-image models reject it", "1.3.1")
  case object Small extends ImageSizeType("256x256")
  @deprecated("dall-e-2 / dall-e-3 only - both shut down; gpt-image models reject it", "1.3.1")
  case object Medium extends ImageSizeType("512x512")
  @deprecated("dall-e-2 / dall-e-3 only - both shut down; gpt-image models reject it", "1.3.1")
  case object LargeLandscape extends ImageSizeType("1792x1024")
  @deprecated("dall-e-2 / dall-e-3 only - both shut down; gpt-image models reject it", "1.3.1")
  case object LargePortrait extends ImageSizeType("1024x1792")
}

sealed trait ImageResponseFormatType extends EnumValue

object ImageResponseFormatType {
  case object url extends ImageResponseFormatType
  case object b64_json extends ImageResponseFormatType
}

sealed trait ImageQualityType extends EnumValue

object ImageQualityType {
  case object low extends ImageQualityType
  case object medium extends ImageQualityType
  case object high extends ImageQualityType
  case object auto extends ImageQualityType

  @deprecated("dall-e-2 / dall-e-3 only - both shut down; gpt-image models reject it", "1.3.1")
  case object standard extends ImageQualityType
  @deprecated("dall-e-2 / dall-e-3 only - both shut down; gpt-image models reject it", "1.3.1")
  case object hd extends ImageQualityType
}

sealed trait ImageStyleType extends EnumValue

object ImageStyleType {
  case object vivid extends ImageStyleType
  case object natural extends ImageStyleType
}
