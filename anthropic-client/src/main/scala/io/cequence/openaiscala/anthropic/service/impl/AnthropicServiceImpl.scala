package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps

import scala.concurrent.Future

private[service] trait AnthropicServiceImpl extends Anthropic {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createMessage(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse] = {
    val bodyParams =
      createBodyParamsForMessageCreation(messages, settings, stream = Some(false))

    execPOST(
      EndPoint.messages,
      bodyParams = bodyParams
    ).map(
      _.asSafeJson[CreateMessageResponse]
    )
  }

  override def createMessageStreamed(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Source[ContentBlockDelta, NotUsed] = {
    val bodyParams =
      createBodyParamsForMessageCreation(messages, settings, stream = Some(true))
    val stringParams = paramTuplesToStrings(bodyParams)

    engine
      .execJsonStream(
        EndPoint.messages.toString(),
        "POST",
        bodyParams = stringParams
      )
      .map(serializeStreamedJson)
      .collect { case Some(delta) => delta }
  }
}
