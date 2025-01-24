package io.cequence.openaiscala.perplexity.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  ChatCompletionResponse
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.perplexity.domain.Message
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.openaiscala.perplexity.domain.settings.{
  SolarResponseFormatType,
  SonarCreateChatCompletionSettings
}
import io.cequence.openaiscala.perplexity.service.SonarService
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}

import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAISonarChatCompletionService(
  underlying: SonarService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    underlying
      .createChatCompletion(
        messages.map(toSonarMessage),
        toSonarSetting(settings)
      )
      .map(toOpenAIResponse)
  }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    underlying
      .createChatCompletionStreamed(
        messages.map(toSonarMessage),
        toSonarSetting(settings)
      )
      .map(toOpenAIResponse)

  private def toSonarMessage(message: BaseMessage): Message =
    message match {
      case SystemMessage(content, _)    => Message.SystemMessage(content)
      case UserMessage(content, _)      => Message.UserMessage(content)
      case AssistantMessage(content, _) => Message.AssistantMessage(content)
      case _ => throw new OpenAIScalaClientException(s"Unsupported message type for Sonar.")
    }

  private def toSonarSetting(settings: CreateChatCompletionSettings)
    : SonarCreateChatCompletionSettings =
    SonarCreateChatCompletionSettings(
      model = settings.model,
      frequency_penalty = settings.frequency_penalty,
      max_tokens = settings.max_tokens,
      presence_penalty = settings.presence_penalty,
      response_format = settings.response_format_type.flatMap {
        case ChatCompletionResponseFormatType.json_object =>
          Some(SolarResponseFormatType.json_schema)

        case ChatCompletionResponseFormatType.json_schema =>
          Some(SolarResponseFormatType.json_schema)

        case ChatCompletionResponseFormatType.text => None
      },
      return_images = None,
      return_related_questions = None,
      search_domain_filter = Nil,
      search_recency_filter = None,
      temperature = settings.temperature,
      top_k = None,
      top_p = settings.top_p
    )

  private def toOpenAIResponse(response: SonarChatCompletionResponse): ChatCompletionResponse =
    ChatCompletionResponse(
      id = response.id,
      created = response.created,
      model = response.model,
      system_fingerprint = None,
      choices = response.choices.map(choice =>
        choice.copy(
          message = choice.message.copy(
            content = s"${choice.message.content}${citationAppendix(response.citations)}"
          )
        )
      ),
      usage = response.usage
    )

  private def toOpenAIResponse(response: SonarChatCompletionChunkResponse)
    : ChatCompletionChunkResponse =
    ChatCompletionChunkResponse(
      id = response.id,
      created = response.created,
      model = response.model,
      system_fingerprint = None,
      choices = response.choices.map(choice =>
        // when finished append the citations
        if (choice.finish_reason.isDefined) {
          choice.copy(
            delta = choice.delta.copy(
              content = Some(
                s"${choice.delta.content.getOrElse("")}${citationAppendix(response.citations)}"
              )
            )
          )
        } else
          choice
      ),
      usage = response.usage
    )

  private def citationAppendix(citations: Seq[String]) =
    s"\n\nCitations:\n${citations.mkString("\n")}"

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
