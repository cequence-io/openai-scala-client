package io.cequence.openaiscala.domain.responsesapi

/**
 * Represents the content of an output message.
 */
sealed trait OutputMessageContent {
  val `type`: String
}

object OutputMessageContent {

  /**
   * A text output from the model.
   *
   * @param annotations
   *   The annotations of the text output.
   * @param text
   *   The text output from the model.
   */
  final case class OutputText(
    annotations: Seq[Annotation] = Nil,
    text: String
  ) extends OutputMessageContent {
    override val `type`: String = "output_text"
  }

  /**
   * A refusal from the model.
   *
   * @param refusal
   *   The refusal explanation from the model.
   */
  final case class Refusal(
    refusal: String
  ) extends OutputMessageContent {
    override val `type`: String = "refusal"
  }
}

sealed trait Annotation {
  val `type`: String
}

object Annotation {

  final case class UrlCitation(
    startIndex: Int,
    endIndex: Int,
    url: String,
    title: String
  ) extends Annotation {
    override val `type`: String = "url_citation"
  }

  final case class FileCitation(
    index: Int,
    fileId: String,
    filename: String
  ) extends Annotation {
    override val `type`: String = "file_citation"
  }
}