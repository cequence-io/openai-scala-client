package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.responsesapi.Input

/**
 * Represents an image generation call made by the model.
 *
 * @param id
 *   The unique ID of the image generation call.
 * @param result
 *   The generated image encoded in base64, or null if not available.
 * @param status
 *   The status of the image generation call.
 */
final case class ImageGenerationToolCall(
  id: String,
  result: Option[String],
  status: String
) extends ToolCall
    with Input {
  val `type`: String = "image_generation_call"
}
