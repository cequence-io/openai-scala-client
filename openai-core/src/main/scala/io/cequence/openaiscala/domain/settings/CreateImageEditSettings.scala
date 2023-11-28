package io.cequence.openaiscala.domain.settings

// same as CrateImageSettings, but wo. quality and style
case class CreateImageEditSettings(
  // The model to use for image generation. Only dall-e-2 is supported at this time.
  model: Option[String] = None,

  // The number of images to generate. Must be between 1 and 10. For dall-e-3, only n=1 is supported. Defaults to 1
  n: Option[Int] = None,

  // The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024 for dall-e-2.
  // Defaults to 1024x1024
  size: Option[ImageSizeType] = None,

  // The format in which the generated images are returned. Must be one of url or b64_json. Defaults to url
  response_format: Option[ImageResponseFormatType] = None,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None
)