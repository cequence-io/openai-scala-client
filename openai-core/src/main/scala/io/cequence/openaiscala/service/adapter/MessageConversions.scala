package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{BaseMessage, SystemMessage, UserMessage}
import org.slf4j.LoggerFactory

object MessageConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  type MessageConversion = Seq[BaseMessage] => Seq[BaseMessage]

  val systemToUserMessages: MessageConversion =
    (messages: Seq[BaseMessage]) => {
      val nonSystemMessages = messages.map {
        case SystemMessage(content, _) =>
          logger.warn(s"System message found but not supported by an underlying model. Converting to a user message instead: '${content}'")
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
}
