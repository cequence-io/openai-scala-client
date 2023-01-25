package io.cequence.openaiscala.domain.settings

case class CreateImageSettings(
  // The number of images to generate. Must be between 1 and 10. Defaults to 1
  n: Option[Int] = None,

  // The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024. Defaults to 1024x1024
  size: Option[ImageSizeType.Value] = None,

  // The format in which the generated images are returned. Must be one of url or b64_json. Defaults to url
  response_format: Option[ResponseFormatType.Value] = None,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None
)

object ImageSizeType extends Enumeration {
  val Small = Value("256x256")
  val Medium = Value("512x512")
  val Large = Value("1024x1024")
}

object ResponseFormatType extends Enumeration {
  val url, b64_json = Value
}