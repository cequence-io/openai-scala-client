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
import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaFormat
import io.cequence.openaiscala.perplexity.domain.Message
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.openaiscala.perplexity.domain.settings.{
  SolarResponseFormat,
  SonarCreateChatCompletionSettings
}
import io.cequence.openaiscala.perplexity.service.{SonarConsts, SonarService}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}
import io.cequence.wsclient.JsonUtil
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAISonarChatCompletionService(
  underlying: SonarService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with SonarConsts {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val addAHrefToCitations = getAHrefCitationParamValue(settings)

    underlying
      .createChatCompletion(
        messages.map(toSonarMessage),
        toSonarSetting(settings)
      )
      .map(toOpenAIResponse(addAHrefToCitations))
  }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] = {
    val addAHrefToCitations = getAHrefCitationParamValue(settings)

    underlying
      .createChatCompletionStreamed(
        messages.map(toSonarMessage),
        toSonarSetting(settings)
      )
      .map(toOpenAIChunkResponse(addAHrefToCitations))
  }

  private def getAHrefCitationParamValue(settings: CreateChatCompletionSettings) =
    settings.extra_params.get(aHrefForCitationsParam).exists(_.asInstanceOf[Boolean])

  private def toSonarMessage(message: BaseMessage): Message =
    message match {
      case SystemMessage(content, _)    => Message.SystemMessage(content)
      case UserMessage(content, _)      => Message.UserMessage(content)
      case AssistantMessage(content, _) => Message.AssistantMessage(content)
      case _ => throw new OpenAIScalaClientException(s"Unsupported message type for Sonar.")
    }

  private def toSonarSetting(settings: CreateChatCompletionSettings)
    : SonarCreateChatCompletionSettings = {
    def jsonSchema = settings.jsonSchema
      .map(_.structure)
      .getOrElse(
        throw new OpenAIScalaClientException("JsonSchema is expected for Sonar.")
      )

    SonarCreateChatCompletionSettings(
      model = settings.model,
      frequency_penalty = settings.frequency_penalty,
      max_tokens = settings.max_tokens,
      presence_penalty = settings.presence_penalty,
      response_format = settings.response_format_type.flatMap {
        case ChatCompletionResponseFormatType.json_object |
            ChatCompletionResponseFormatType.json_schema =>
          Some(
            SolarResponseFormat.JsonSchema(
              JsonUtil.toValueMap(Json.toJson(jsonSchema).as[JsObject])
            )
          )

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
  }

  private def toOpenAIResponse(
    addAHrefToCitations: Boolean
  )(
    response: SonarChatCompletionResponse
  ): ChatCompletionResponse =
    ChatCompletionResponse(
      id = response.id,
      created = response.created,
      model = response.model,
      system_fingerprint = None,
      choices = response.choices.map(choice =>
        choice.copy(
          message = choice.message.copy(
            content =
              s"${choice.message.content}${citationAppendix(response.citations, addAHrefToCitations)}"
          )
        )
      ),
      usage = response.usage
    )

  private def toOpenAIChunkResponse(
    addAHrefToCitations: Boolean
  )(
    response: SonarChatCompletionChunkResponse
  ): ChatCompletionChunkResponse =
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
                s"${choice.delta.content.getOrElse("")}${citationAppendix(response.citations, addAHrefToCitations)}"
              )
            )
          )
        } else
          choice
      ),
      usage = response.usage
    )

  private def citationAppendix(
    citations: Seq[String],
    addAHref: Boolean
  ) = {
    val citationsPart = citations.map { citation =>
      if (addAHref) s"""<a href="$citation">$citation</a>""" else citation
    }.mkString("\n")

    s"\n\nCitations:\n${citationsPart}"
  }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
