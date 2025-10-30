package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.wsclient.domain.EnumValue

/**
 * A tool that generates images based on a text prompt using a diffusion model.
 *
 * @param background
 *   Optional background type for the generated image. One of transparent, opaque, or auto.
 *   Defaults to auto if not specified.
 * @param inputFidelity
 *   Optional guidance for how much the input image influences the output. One of "low",
 *   "medium", or "high".
 * @param inputImageMask
 *   Optional input image mask for inpainting/editing. Specifies which parts of the image to
 *   modify.
 * @param model
 *   Optional image generation model to use. Defaults to "gpt-image-1" if not specified.
 * @param moderation
 *   Optional guidance for moderation. Can be "none" or "auto". Defaults to "auto" if not
 *   specified.
 * @param outputCompression
 *   Optional compression level for the output image (0-100). Defaults to 100 if not specified.
 * @param outputFormat
 *   Optional format for the output image. One of "png", "jpeg", or "webp". Defaults to "png"
 *   if not specified.
 * @param partialImages
 *   Optional number of partial/intermediate images to return during generation. Defaults to 0
 *   if not specified.
 * @param quality
 *   Optional quality guidance for the output. Can be "low", "medium", "high", or "auto".
 *   Defaults to "auto" if not specified.
 * @param size
 *   Optional size guidance for the output image. Can be specific dimensions or "auto".
 *   Defaults to "auto" if not specified.
 */
case class ImageGenerationTool(
  background: Option[ImageGenerationBackground] = None,
  inputFidelity: Option[String] = None,
  inputImageMask: Option[InputImageMask] = None,
  model: Option[String] = None,
  moderation: Option[String] = None,
  outputCompression: Option[Int] = None,
  outputFormat: Option[String] = None,
  partialImages: Option[Int] = None,
  quality: Option[String] = None,
  size: Option[String] = None
) extends Tool {
  val `type`: String = "image_generation"

  override def typeString: String = `type`
}

/**
 * Input image mask for inpainting/editing operations.
 *
 * @param fileId
 *   Optional file ID of the uploaded mask image.
 * @param imageUrl
 *   Optional URL of the mask image.
 */
case class InputImageMask(
  fileId: Option[String] = None,
  imageUrl: Option[String] = None
)

/**
 * Background type for the generated image.
 */
sealed trait ImageGenerationBackground extends EnumValue

object ImageGenerationBackground {
  case object transparent extends ImageGenerationBackground
  case object opaque extends ImageGenerationBackground
  case object auto extends ImageGenerationBackground
}
