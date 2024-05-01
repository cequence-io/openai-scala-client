package io.cequence.openaiscala.v2.domain.response

sealed trait ResponseFormat

object ResponseFormat {

  /** The default response format. */
  case object StringResponse extends ResponseFormat

  /** The model can return text or any value needed. */
  case object TextResponse extends ResponseFormat

  /**
   * For JSON object responses, only function type tools are allowed to be passed to the Run.
   *
   * Important: You must also instruct the model to produce JSON yourself via a system or user
   * message. Without this, the model may generate an unending stream of whitespace until the
   * generation reaches the token limit, resulting in a long-running and seemingly "stuck"
   * request. Also note that the message content may be partially cut off if
   * finish_reason="length", which indicates the generation exceeded max_tokens or the
   * conversation exceeded the max context length.
   */
  case object JsonObjectResponse extends ResponseFormat

}
