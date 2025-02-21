package io.cequence.openaiscala.gemini.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.BaseMessage.getTextContent
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChunkMessageSpec,
  PromptTokensDetails,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  DeveloperMessage,
  ImageURLContent,
  SystemMessage,
  TextContent,
  UserMessage,
  UserSeqMessage,
  ChatRole => OpenAIChatRole
}
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.Part.{FileData, InlineData}
import io.cequence.openaiscala.gemini.domain.response.{GenerateContentResponse, UsageMetadata}
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.domain.{CachedContent, ChatRole, Content, Part}
import io.cequence.openaiscala.gemini.service.GeminiService
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}

import scala.concurrent.{ExecutionContext, Future}
import io.cequence.openaiscala.domain.settings.ChatCompletionResponseFormatType
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.gemini.domain.Schema
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.gemini.domain.SchemaType
import org.slf4j.LoggerFactory

import scala.collection.immutable.Traversable

private[service] class OpenAIGeminiChatCompletionService(
  underlying: GeminiService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

  protected val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val (userMessages, systemMessage) = splitMessage(messages)

    for {
      settings <- handleCaching(systemMessage, userMessages, settings)

      response <- underlying.generateContent(
        userMessages.map(toGeminiContent),
        settings
      )
    } yield toOpenAIResponse(response)
  }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] = {
    val (userMessages, systemMessage) = splitMessage(messages)

    val futureSource = handleCaching(systemMessage, userMessages, settings).map(settings =>
      underlying
        .generateContentStreamed(
          userMessages.map(toGeminiContent),
          settings
        )
        .map(toOpenAIChunkResponse)
    )

    // keep it like this because of the compatibility with older versions of Akka stream
    Source.fromFutureSource(futureSource).mapMaterializedValue(_ => NotUsed)
  }

  private def handleCaching(
    systemMessage: Option[BaseMessage],
    userMessages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[GenerateContentSettings] =
    if (settings.geminiCacheSystemMessage && systemMessage.isDefined) {
      // we cache only the system message
      cacheMessages(systemMessage.get, userMessage = None, settings).map { cacheName =>
        // we skip the system message, as it is cached, plus we set the cache name
        toGeminiSettings(settings, systemMessage = None).copy(cachedContent = Some(cacheName))
      }
    } else
      Future.successful(
        // no cache, we pass the system message
        toGeminiSettings(settings, systemMessage)
      )

  // returns the cache name
  private def cacheMessages(
    systemMessage: BaseMessage,
    userMessage: Option[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[String] = {
    val systemMessageContent = getTextContent(systemMessage).getOrElse(
      throw new OpenAIScalaClientException("System message content is missing.")
    )
    val userMessageContent = userMessage.flatMap(getTextContent)

    underlying
      .createCachedContent(
        CachedContent(
          // the first is considered the system message
          systemInstruction = Some(Content.textPart(systemMessageContent, User)),
          // the rest goes to the user messages/contents
          contents = userMessageContent
            .map(content => Seq(Content.textPart(content, User)))
            .getOrElse(Nil),
          model = settings.model
        )
      )
      .map(_.name.get)
  }

  private def splitMessage(messages: Seq[BaseMessage])
    : (Seq[BaseMessage], Option[BaseMessage]) = {
    val (systemMessages, userMessages) = messages.partition {
      case _: SystemMessage    => true
      case _: DeveloperMessage => true
      case _                   => false
    }

    if (systemMessages.size > 1)
      throw new OpenAIScalaClientException("Only one system message is supported.")

    (userMessages, systemMessages.headOption)
  }

  private def toGeminiContent(message: BaseMessage): Content =
    message match {
      case SystemMessage(content, _) =>
        Content(Seq(Part.Text(content)), Some(ChatRole.User))

      case DeveloperMessage(content, _) =>
        Content(Seq(Part.Text(content)), Some(ChatRole.User))

      case UserMessage(content, _) =>
        Content(Seq(Part.Text(content)), Some(ChatRole.User))

      case UserSeqMessage(content, _) =>
        val parts = content.map {
          case TextContent(content) => Part.Text(content)
          case ImageURLContent(url) =>
            if (url.startsWith("data:")) {
              val mediaTypeEncodingAndData = url.drop(5)
              val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
              val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
              val encoding = encodingAndData.takeWhile(_ != ',')
              val data = encodingAndData.drop(encoding.length + 1)

              InlineData(
                mimeType = mediaType,
                data = data
              )
            } else
              FileData(
                mimeType = None,
                fileUri = url
              )
        }

        Content(parts, Some(ChatRole.User))

      case AssistantMessage(content, _) =>
        Content(Seq(Part.Text(content)), Some(ChatRole.Model))

      case _ => throw new OpenAIScalaClientException(s"Unsupported message type for Gemini.")
    }

  private def toGeminiSettings(
    settings: CreateChatCompletionSettings,
    systemMessage: Option[BaseMessage]
  ): GenerateContentSettings = {

    // handle json schema
    val responseFormat =
      settings.response_format_type.getOrElse(ChatCompletionResponseFormatType.text)

    val jsonSchema =
      if (
        responseFormat == ChatCompletionResponseFormatType.json_schema && settings.jsonSchema.isDefined
      ) {
        settings.jsonSchema.get.structure match {
          case Left(schema) =>
            Some(toGeminiJSONSchema(schema))
          case Right(_) =>
            logger.warn(
              "Map-like legacy JSON schema is not supported for conversion to Gemini schema."
            )
            None
        }
      } else
        None

    // check for unsupported fields
    checkNotSupported(settings)

    GenerateContentSettings(
      model = settings.model,
      tools = None, // TODO
      toolConfig = None, // TODO
      safetySettings = None,
      systemInstruction = systemMessage.map(toGeminiContent),
      generationConfig = Some(
        GenerationConfig(
          stopSequences = (if (settings.stop.nonEmpty) Some(settings.stop) else None),
          responseMimeType = None,
          responseSchema = jsonSchema,
          responseModalities = None,
          candidateCount = settings.n,
          maxOutputTokens = settings.max_tokens,
          temperature = settings.temperature,
          topP = settings.top_p,
          topK = None,
          seed = settings.seed,
          presencePenalty = settings.presence_penalty,
          frequencyPenalty = settings.frequency_penalty,
          responseLogprobs = settings.logprobs,
          logprobs = settings.top_logprobs,
          enableEnhancedCivicAnswers = None,
          speechConfig = None
        )
      ),
      cachedContent = None
    )
  }

  private def checkNotSupported(
    settings: CreateChatCompletionSettings
  ) = {
    def notSupported(
      field: CreateChatCompletionSettings => Option[_],
      fieldName: String
    ): Unit =
      field(settings).foreach { _ =>
        logger.warn(s"OpenAI param '$fieldName' is not yet supported by Gemini. Skipping...")
      }

    def notSupportedCollection(
      field: CreateChatCompletionSettings => Traversable[_],
      fieldName: String
    ): Unit =
      if (field(settings).nonEmpty) {
        logger.warn(s"OpenAI param '$fieldName' is not yet supported by Gemini. Skipping...")
      }

    notSupported(_.reasoning_effort, "reasoning_effort")
    notSupported(_.service_tier, "service_tier")
    notSupported(_.parallel_tool_calls, "parallel_tool_calls")
    notSupportedCollection(_.metadata, "metadata")
    notSupportedCollection(_.logit_bias, "logit_bias")
    notSupported(_.user, "user")
    notSupported(_.store, "store")
  }

  private def toGeminiJSONSchema(
    jsonSchema: JsonSchema
  ): Schema = jsonSchema match {
    case JsonSchema.String(description, enumVals) =>
      Schema(
        `type` = SchemaType.STRING,
        description = description,
        `enum` = Some(enumVals)
      )

    case JsonSchema.Number(description) =>
      Schema(
        `type` = SchemaType.NUMBER,
        description = description
      )

    case JsonSchema.Integer(description) =>
      Schema(
        `type` = SchemaType.INTEGER,
        description = description
      )

    case JsonSchema.Boolean(description) =>
      Schema(
        `type` = SchemaType.BOOLEAN,
        description = description
      )

    case JsonSchema.Object(properties, required) =>
      Schema(
        `type` = SchemaType.OBJECT,
        properties = Some(
          properties.map { case (key, jsonSchema) =>
            key -> toGeminiJSONSchema(jsonSchema)
          }.toMap
        ),
        required = Some(required)
      )

    case JsonSchema.Array(items) =>
      Schema(
        `type` = SchemaType.ARRAY,
        items = Some(toGeminiJSONSchema(items))
      )

    case _ =>
      throw new OpenAIScalaClientException(s"Unsupported JSON schema type for Gemini.")
  }

  private def toOpenAIResponse(
    response: GenerateContentResponse
  ): ChatCompletionResponse =
    ChatCompletionResponse(
      id = "gemini",
      created = new java.util.Date(),
      model = response.modelVersion,
      system_fingerprint = None,
      choices = response.candidates.map { candidate =>
        ChatCompletionChoiceInfo(
          index = candidate.index.getOrElse(0),
          message = toOpenAIAssistantMessage(candidate.content),
          finish_reason = candidate.finishReason.map(_.toString),
          logprobs = None
        )
      },
      usage = Some(toOpenAIUsage(response.usageMetadata))
    )

  private def toOpenAIChunkResponse(
    response: GenerateContentResponse
  ): ChatCompletionChunkResponse =
    ChatCompletionChunkResponse(
      id = "gemini",
      created = new java.util.Date(),
      model = response.modelVersion,
      system_fingerprint = None,
      choices = response.candidates.map { candidate =>
        ChatCompletionChoiceChunkInfo(
          index = candidate.index.getOrElse(0),
          delta = toOpenAIAssistantChunkMessage(candidate.content),
          finish_reason = candidate.finishReason.map(_.toString)
        )
      },
      usage = Some(toOpenAIUsage(response.usageMetadata))
    )

  private def toOpenAIAssistantMessage(
    content: Content
  ): AssistantMessage =
    AssistantMessage(
      content.parts.collect {
        case Part.Text(text) => text
        case _ =>
          throw new OpenAIScalaClientException(
            s"Unsupported assistant part type for Gemini. Implement me!"
          )
      }.mkString("\n")
    )

  private def toOpenAIAssistantChunkMessage(
    content: Content
  ): ChunkMessageSpec = {
    val texts = content.parts.collect {
      case Part.Text(text) => text
      case _ =>
        throw new OpenAIScalaClientException(
          s"Unsupported assistant part type for Gemini. Implement me!"
        )
    }

    ChunkMessageSpec(
      Some(OpenAIChatRole.Assistant),
      if (texts.nonEmpty) Some(texts.mkString("\n")) else None
    )
  }

  private def toOpenAIUsage(
    usageMetadata: UsageMetadata
  ) =
    OpenAIUsageInfo(
      prompt_tokens = usageMetadata.promptTokenCount,
      total_tokens = usageMetadata.totalTokenCount,
      completion_tokens = usageMetadata.candidatesTokenCount,
      prompt_tokens_details = Some(
        PromptTokensDetails(
          cached_tokens = usageMetadata.cachedContentTokenCount.getOrElse(0),
          audio_tokens = None
        )
      )
    )

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
