package io.cequence.openaiscala.domain.responsesapi

sealed trait InputMessageContent {
  val `type`: String
}

object InputMessageContent {

  /**
   * A text input to the model.
   *
   * @param text
   *   The text input to the model.
   */
  final case class Text(
    text: String
  ) extends InputMessageContent {
    val `type`: String = "input_text"
  }

  /**
   * An image input to the model.
   *
   * @param fileId
   *   The ID of the file to be sent to the model.
   * @param imageUrl
   *   The URL of the image to be sent to the model. A fully qualified URL or base64 encoded
   *   image in a data URL.
   * @param detail
   *   The detail level of the image to be sent to the model.
   */
  final case class Image(
    fileId: Option[String] = None,
    imageUrl: Option[String] = None,
    detail: Option[String] = None // low, high, auto
  ) extends InputMessageContent {
    val `type`: String = "input_image"
  }

  /**
   * A file input to the model.
   *
   * @param fileData
   *   The base64-encoded data of the file to be sent to the model.
   * @param fileId
   *   The ID of the file to be sent to the model.
   * @param fileUrl
   *   The URL of the file to be sent to the model.
   * @param filename
   *   The name of the file to be sent to the model.
   */
  final case class File(
    fileData: Option[String] = None,
    fileId: Option[String] = None,
    fileUrl: Option[String] = None,
    filename: Option[String] = None
  ) extends InputMessageContent {
    val `type`: String = "input_file"
  }
}
