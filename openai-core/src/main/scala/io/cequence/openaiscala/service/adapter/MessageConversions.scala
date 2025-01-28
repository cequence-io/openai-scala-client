package io.cequence.openaiscala.service.adapter

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.cequence.openaiscala.domain.response.ChunkMessageSpec
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  SystemMessage,
  UserMessage
}
import org.slf4j.LoggerFactory

object MessageConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  type MessagesConversion = Seq[BaseMessage] => Seq[BaseMessage]

  val systemToUserMessages: MessagesConversion =
    (messages: Seq[BaseMessage]) => {
      val nonSystemMessages = messages.map {
        case SystemMessage(content, _) =>
          logger.warn(
            s"System message found but not supported by an underlying model. Converting to a user message instead: '${content}'"
          )
          UserMessage(s"System: ${content}")

        case x: BaseMessage => x
      }

      // there cannot be two consecutive user messages, so we need to merge them
      nonSystemMessages.foldLeft(Seq.empty[BaseMessage]) {
        case (acc, UserMessage(content, _)) if acc.nonEmpty =>
          acc.last match {
            case UserMessage(lastContent, _) =>
              acc.init :+ UserMessage(lastContent + "\n" + content)
            case _ =>
              acc :+ UserMessage(content)
          }

        case (acc, message) => acc :+ message
      }
    }

  lazy val thinkEndTagRegex = "(?<!['\"])</think>(?!['\"])"

  val filterOutToThinkEnd: AssistantMessage => AssistantMessage =
    (message: AssistantMessage) => {
      val newContent = message.content.split(thinkEndTagRegex).last.trim
      message.copy(content = newContent)
    }

  def filterOutToThinkEndFlow: Flow[Seq[ChunkMessageSpec], Seq[ChunkMessageSpec], NotUsed] = {
    Flow[Seq[ChunkMessageSpec]].statefulMapConcat { () =>
      var foundEnd = false

      (messages: Seq[ChunkMessageSpec]) => {
        if (foundEnd) {
          List(messages)
        } else {
          val endFoundInThisChunk =
            messages.exists(_.content.exists(_.trim.matches(thinkEndTagRegex)))

          if (endFoundInThisChunk) {
            foundEnd = true
          }
          List(messages.map(_.copy(content = None)))
        }
      }
    }
  }
}
