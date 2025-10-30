package io.cequence.openaiscala.domain.graders

import io.cequence.wsclient.domain.EnumValue

/**
 * Base trait for grader input content types.
 */
sealed trait GraderInputContent {
  val `type`: String
}

object GraderInputContent {

  /**
   * A simple text input to the model.
   *
   * @param text
   *   The text content.
   */
  case class TextString(text: String) extends GraderInputContent {
    override val `type`: String = "text"
  }

  /**
   * A text input to the model.
   *
   * @param text
   *   The text input to the model.
   */
  case class InputText(text: String) extends GraderInputContent {
    override val `type`: String = "input_text"
  }

  /**
   * A text output from the model.
   *
   * @param text
   *   The text output from the model.
   */
  case class OutputText(text: String) extends GraderInputContent {
    override val `type`: String = "output_text"
  }

  /**
   * An image input to the model.
   *
   * @param imageUrl
   *   The URL of the image input.
   * @param detail
   *   The detail level of the image to be sent to the model. One of high, low, or auto.
   *   Defaults to auto.
   */
  case class InputImage(
    imageUrl: String,
    detail: Option[ImageDetail] = None
  ) extends GraderInputContent {
    override val `type`: String = "input_image"
  }

  /**
   * An audio input to the model.
   *
   * @param inputAudio
   *   The audio input object.
   */
  case class InputAudio(
    inputAudio: AudioInput
  ) extends GraderInputContent {
    override val `type`: String = "input_audio"
  }

  /**
   * An array of inputs, each of which may be either an input text, input image, or input audio
   * object.
   *
   * @param items
   *   The list of input content items.
   */
  case class ContentArray(
    items: Seq[GraderInputContent]
  ) extends GraderInputContent {
    override val `type`: String = "array"
  }
}

/**
 * Image detail level.
 */
sealed trait ImageDetail extends EnumValue

object ImageDetail {
  case object high extends ImageDetail
  case object low extends ImageDetail
  case object auto extends ImageDetail

  def values: Seq[ImageDetail] = Seq(high, low, auto)
}

/**
 * Audio input object.
 *
 * @param data
 *   Base64-encoded audio data.
 * @param format
 *   The format of the audio (e.g., wav, mp3).
 */
case class AudioInput(
  data: String,
  format: String
)
