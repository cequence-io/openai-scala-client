package io.cequence.openaiscala.domain.settings

// same as CrateImageSettings, but wo. quality and style
case class CreateImageEditSettings(
  // The model to use for image editing (required - dall-e-2 is shut down), e.g. gpt-image-2
  model: Option[String] = None,

  // The number of images to generate. Must be between 1 and 10. Defaults to 1
  n: Option[Int] = None,

  // The size of the generated images: Large, Landscape, Portrait or Auto for gpt-image models. Defaults to auto
  size: Option[ImageSizeType] = None,

  // dall-e only (shut down): gpt-image models reject it and always return b64_json
  response_format: Option[ImageResponseFormatType] = None,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None
)
