package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.ThreadAndRun.Content.{ContentBlock, ContentBlocks}

final case class ThreadAndRun(
  // TODO: check whether the message model is restrictive enough
  messages: Seq[ThreadAndRun.Message],
  toolResources: Seq[AssistantToolResource],
  metadata: Map[String, Any]
)

object ThreadAndRun {

  sealed abstract class Message private (
                                          val role: ThreadAndRunRole,
                                          val content: Content,
                                          val attachments: Seq[Attachment],
                                          val metadata: Map[String, Any]
  )

  object Message {

    case class UserMessage(
      contentString: String,
      override val attachments: Seq[Attachment] = Seq.empty,
      override val metadata: Map[String, Any] = Map.empty
    ) extends Message(ChatRole.User, Content.SingleString(contentString), Seq.empty, Map.empty)

    case class UserMessageContent(
      contentBlocks: Seq[ContentBlock],
      override val attachments: Seq[Attachment] = Seq.empty,
      override val metadata: Map[String, Any] = Map.empty
    ) extends Message(
          ChatRole.User,
          Content.ContentBlocks(contentBlocks),
          Seq.empty,
          Map.empty
        )

    case class AssistantMessage(
      contentString: String,
      override val attachments: Seq[Attachment] = Seq.empty,
      override val metadata: Map[String, Any] = Map.empty
    ) extends Message(ChatRole.Assistant, Content.SingleString(contentString), Seq.empty, Map.empty)

    case class AssistantMessageContent(
      contentBlocks: Seq[ContentBlock],
      override val attachments: Seq[Attachment] = Seq.empty,
      override val metadata: Map[String, Any] = Map.empty
    ) extends Message(
          ChatRole.Assistant,
          Content.ContentBlocks(contentBlocks),
          Seq.empty,
          Map.empty
        )

  }

  sealed trait Content

  case object Content {
    final case class SingleString(text: String) extends Content
    final case class ContentBlocks(blocks: Seq[ContentBlock]) extends Content

    sealed trait ContentBlock
    object ContentBlock {
      final case class TextBlock(text: String) extends ContentBlock
      final case class ImageFileBlock(
        fileId: String,
        detail: ImageFileDetail
      ) extends ContentBlock
      final case class ImageUrl(
        `type`: String,
        url: String,
        detail: ImageDetail
      )

      sealed trait ImageDetail
      sealed trait ImageFileDetail

      object ImageDetail {
        case object Auto extends ImageDetail
        case object Low extends ImageDetail with ImageFileDetail
        case object High extends ImageDetail with ImageFileDetail
      }
    }
  }

}
