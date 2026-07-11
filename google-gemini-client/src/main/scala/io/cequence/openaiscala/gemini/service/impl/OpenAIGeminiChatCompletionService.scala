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
  CompletionTokenDetails,
  PromptTokensDetails,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus,
  ChatCompletionTool,
  DeveloperMessage,
  FileContent,
  ImageURLContent,
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  TextContent,
  ToolMessage,
  UserMessage,
  UserSeqMessage,
  ChatRole => OpenAIChatRole
}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.Part.{FileData, InlineData}
import io.cequence.openaiscala.gemini.domain.response.{GenerateContentResponse, UsageMetadata}
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.settings.{
  FunctionCallingMode,
  GenerateContentSettings,
  GenerationConfig,
  ThinkingConfig,
  ToolConfig
}
import io.cequence.openaiscala.gemini.JsonFormats.{
  batchRpcErrorReads,
  generateContentResponseFormat
}
import io.cequence.openaiscala.gemini.domain.{
  BatchRequestItem,
  BatchRpcError,
  BatchState,
  CachedContent,
  ChatRole,
  Content,
  GenerateContentBatch,
  Part,
  ThinkingLevel
}
import io.cequence.openaiscala.gemini.service.GeminiService
import io.cequence.openaiscala.service.{
  HasOpenAIConfig,
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}

import scala.concurrent.{ExecutionContext, Future}
import io.cequence.openaiscala.domain.settings.ChatCompletionResponseFormatType
import io.cequence.openaiscala.domain.FunctionCallSpec
import io.cequence.openaiscala.domain.response.{
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.gemini.domain.{FunctionDeclaration, Tool => GeminiTool}
import io.cequence.openaiscala.gemini.domain.Schema
import io.cequence.wsclient.JsonUtil
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.gemini.domain.SchemaType
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json}

import scala.collection.immutable.Traversable

private[service] class OpenAIGeminiChatCompletionService(
  underlying: GeminiService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with OpenAIChatCompletionBatchService
    with HasOpenAIConfig {

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
  }.recoverWith(repackAsOpenAIException)

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] = {
    val (userMessages, systemMessage) = splitMessage(messages)

    val futureSource = handleCaching(systemMessage, userMessages, settings)
      .map(settings =>
        underlying
          .generateContentStreamed(
            userMessages.map(toGeminiContent),
            settings
          )
          .map(toOpenAIChunkResponse)
      )
      .recoverWith(repackAsOpenAIException)

    // keep it like this because of the compatibility with older versions of Akka stream
    Source.fromFutureSource(futureSource).mapMaterializedValue(_ => NotUsed)
  }

  // only system message is cached
  private def handleCaching(
    systemMessage: Option[BaseMessage],
    userMessages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[GenerateContentSettings] =
    settings.getSystemCacheName.map { cacheName =>
      // we use the cached system message instead of the provided one
      logger.info(s"Using a system message for Gemini from cache: $cacheName")
      Future.successful(
        toGeminiSettings(settings, systemMessage = None).copy(cachedContent = Some(cacheName))
      )
    }.getOrElse(
      if (settings.isCacheSystemMessageEnabled && systemMessage.isDefined) {
        // we cache only the system message
        cacheMessages(systemMessage.get, userMessage = None, settings).map { cacheName =>
          logger.info(s"System message for Gemini cached as: $cacheName")

          // we skip the system message, as it is cached, plus we set the cache name
          toGeminiSettings(settings, systemMessage = None)
            .copy(cachedContent = Some(cacheName))
        }
      } else {
        if (settings.isCacheSystemMessageEnabled)
          logger.warn("No system message provided for caching.")

        Future.successful(
          // no cache, we pass the system message
          toGeminiSettings(settings, systemMessage)
        )
      }
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

  // -- Batch processing (provider-agnostic) --

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] = {
    // each inlined batch request is a full generateContent request and supports its own
    // systemInstruction (see BatchRequestItem), so - unlike a shared, batch-wide one - a
    // request's own system message no longer has to match every other request's
    val representativeSystemMessage =
      requests.headOption.flatMap(request => splitMessage(request.messages)._2)

    // handleCaching resolves the explicit-caching route, which is the one that works with
    // batches (implicit caching does not apply to Batch Mode): a cached-content reference
    // (setSystemCacheName) - or a cache freshly created from the system message
    // (enableCacheSystemMessage), taken from the first request - replaces the per-request
    // system message for every item. This mirrors handleCaching's own branch selection, so
    // that it agrees below on whether caching actually kicks in.
    val cachingActive =
      settings.getSystemCacheName.isDefined ||
        (settings.isCacheSystemMessageEnabled && representativeSystemMessage.isDefined)

    val items = requests.map { request =>
      val (userMessages, systemMessage) = splitMessage(request.messages)
      BatchRequestItem(
        key = request.customId,
        contents = userMessages.map(toGeminiContent),
        // while a cache is in use, the cached (or freshly cached) system message replaces
        // the per-request one for every item, exactly as before - so no per-item override
        // in that case; otherwise each item carries its own system message, if any
        systemInstruction = if (cachingActive) None else systemMessage.map(toGeminiContent)
      )
    }

    (for {
      // outside of caching, no batch-wide systemInstruction is passed on - each item now
      // carries its own (see above), so one request's system prompt can no longer leak into
      // another's
      geminiSettings <- handleCaching(
        if (cachingActive) representativeSystemMessage else None,
        Nil,
        settings
      )

      batch <- underlying.createBatchGenerateContent(
        displayName = "openai-scala-client chat-completion batch",
        requests = items,
        settings = geminiSettings
      )
    } yield toBatchInfo(batch)).recoverWith(repackAsOpenAIException)
  }

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    underlying.getBatch(batchId).map(toBatchInfo).recoverWith(repackAsOpenAIException)

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    underlying
      .getBatch(batchId)
      .flatMap { batch =>
        val output = batch.output.getOrElse(
          throw new OpenAIScalaClientException(
            s"Batch '$batchId' has no output (state: ${batch.state.map(_.toString).getOrElse("unknown")})."
          )
        )

        output.responsesFile match {
          // file-based batches (large payloads) produce a downloadable results file
          case Some(fileName) =>
            underlying.downloadFile(fileName).map { content =>
              content
                .split("\n")
                .toSeq
                .map(_.trim)
                .filter(_.nonEmpty)
                .map(Json.parse)
                .zipWithIndex
                .map { case (json, index) =>
                  toBatchResultItem(
                    key = (json \ "key")
                      .asOpt[String]
                      .orElse((json \ "metadata" \ "key").asOpt[String]),
                    response = (json \ "response").asOpt[GenerateContentResponse],
                    error = (json \ "error").asOpt[BatchRpcError],
                    index = index
                  )
                }
            }

          case None =>
            Future.successful(
              output.inlinedResponses.zipWithIndex.map { case (inlined, index) =>
                toBatchResultItem(inlined.key, inlined.response, inlined.error, index)
              }
            )
        }
      }
      .recoverWith(repackAsOpenAIException)

  private def toBatchResultItem(
    key: Option[String],
    response: Option[GenerateContentResponse],
    error: Option[BatchRpcError],
    index: Int
  ): ChatCompletionBatchResultItem = {
    val customId = key.getOrElse(index.toString)

    val result = (response, error) match {
      case (Some(response), None) =>
        Right(toOpenAIResponse(response))
      case (_, Some(error)) =>
        Left(
          ChatCompletionBatchError(
            error.message.getOrElse(s"Request failed: $error"),
            error.code.map(_.toString)
          )
        )
      case _ =>
        Left(
          ChatCompletionBatchError("The batch response carries no response and no error.")
        )
    }

    ChatCompletionBatchResultItem(customId, result)
  }

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    (for {
      _ <- underlying.cancelBatch(batchId)
      batch <- underlying.getBatch(batchId)
    } yield toBatchInfo(batch)).recoverWith(repackAsOpenAIException)

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    (for {
      batch <- underlying.getBatch(batchId)

      // file-based batches leave a staged input file and a generated results file behind -
      // clean both up (best-effort) along with the batch
      batchFiles = batch.inputFileName.toSeq ++ batch.output.flatMap(_.responsesFile)

      _ <- Future.traverse(batchFiles) { fileName =>
        underlying.deleteFile(fileName).recover { case e =>
          logger.warn(s"Failed to delete the batch file '$fileName': ${e.getMessage}")
        }
      }

      _ <- underlying.deleteBatch(batchId)
    } yield ()).recoverWith(repackAsOpenAIException)

  private def toBatchInfo(batch: GenerateContentBatch): ChatCompletionBatchInfo = {
    val status = batch.state match {
      case Some(BatchState.BATCH_STATE_SUCCEEDED) => ChatCompletionBatchStatus.Completed
      case Some(BatchState.BATCH_STATE_FAILED)    => ChatCompletionBatchStatus.Failed
      case Some(BatchState.BATCH_STATE_CANCELLED) => ChatCompletionBatchStatus.Cancelled
      case Some(BatchState.BATCH_STATE_EXPIRED)   => ChatCompletionBatchStatus.Expired
      // pending, running, unspecified, or absent
      case _ => ChatCompletionBatchStatus.InProgress
    }

    ChatCompletionBatchInfo(
      batch.name,
      status,
      batch.state.map(_.toString).getOrElse("unknown")
    )
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

          case FileContent(_, Some(fileData), _) if fileData.startsWith("data:") =>
            val mediaTypeEncodingAndData = fileData.drop(5)
            val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
            val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
            val encoding = encodingAndData.takeWhile(_ != ',')
            val data = encodingAndData.drop(encoding.length + 1)

            if (encoding != "base64") {
              throw new IllegalArgumentException(
                s"FileContent for Gemini: only base64-encoded data URLs are supported, got '$encoding'."
              )
            }

            InlineData(mimeType = mediaType, data = data)

          case _: FileContent =>
            throw new IllegalArgumentException(
              "FileContent for Gemini: only base64 fileData as a data URL is supported " +
                "(e.g. data:application/pdf;base64,...). OpenAI file_id is not portable."
            )
        }

        Content(parts, Some(ChatRole.User))

      case AssistantMessage(content, _, _) =>
        Content(Seq(Part.Text(content)), Some(ChatRole.Model))

      case AssistantToolMessage(content, _, toolCalls) =>
        val parts = collection.mutable.ArrayBuffer.empty[Part]
        content.foreach(text => parts += Part.Text(text))
        toolCalls.foreach { case (id, spec) =>
          spec match {
            case FunctionCallSpec(name, arguments) =>
              val args = parseJsonObjectToMap(arguments, s"function_call args for '$name'")
              parts += Part.FunctionCall(
                id = Some(id),
                name = name,
                args = args
              )
            case other =>
              logger.warn(
                s"Unsupported tool call spec for Gemini: ${other.getClass.getSimpleName}"
              )
          }
        }
        Content(parts.toSeq, Some(ChatRole.Model))

      case ToolMessage(content, toolCallId, name) =>
        val response = content
          .map(parseJsonObjectToMap(_, s"function_response for '$name'"))
          .getOrElse(Map.empty)
        Content(
          Seq(
            Part.FunctionResponse(
              id = Some(toolCallId),
              name = name,
              response = response
            )
          ),
          Some(ChatRole.User)
        )

      case _ => throw new OpenAIScalaClientException("Unsupported message type for Gemini.")
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
        val jsonSchemaDef = settings.jsonSchema.get

        jsonSchemaDef.structure match {
          case Left(schema) =>
            if (jsonSchemaDef.strict)
              logger.warn(
                "OpenAI's 'strict' mode is not supported by Gemini. The schema will be used without strict validation. Note: Gemini does not support 'additionalProperties'."
              )

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
      tools = settings.getGeminiTools,
      toolConfig = settings.getGeminiToolConfig,
      safetySettings = None,
      systemInstruction = systemMessage.map(toGeminiContent),
      generationConfig = Some(
        GenerationConfig(
          stopSequences = (if (settings.stop.nonEmpty) Some(settings.stop) else None),
          responseMimeType = if (jsonSchema.isDefined) Some("application/json") else None,
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
          speechConfig = None,
          thinkingConfig = toThinkingConfig(
            settings.model,
            settings.reasoning_effort
          )
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

    // reasoning_effort is now supported via thinkingConfig conversion
    notSupported(_.service_tier, "service_tier")
    notSupported(_.parallel_tool_calls, "parallel_tool_calls")
    notSupportedCollection(_.metadata, "metadata")
    notSupportedCollection(_.logit_bias, "logit_bias")
    notSupported(_.user, "user")
    notSupported(_.store, "store")
  }

  /**
   * Converts OpenAI's reasoning_effort to Gemini's ThinkingConfig.
   *
   * Gemini 3.x models use `thinkingLevel` (MINIMAL/LOW/MEDIUM/HIGH); MINIMAL is only valid on
   * Flash variants, not Pro. Gemini 2.5 uses `thinkingBudget` (token count) from config.
   * Setting both fields on Gemini 3 can return an error, so only one is populated.
   *
   * @return
   *   ThinkingConfig, or None if reasoning_effort is None or model doesn't support thinking
   */
  private def toThinkingConfig(
    model: String,
    reasoningEffort: Option[ReasoningEffort]
  ): Option[ThinkingConfig] = reasoningEffort.flatMap { effort =>
    if (isGemini3(model))
      toThinkingLevelConfig(model, effort)
    else if (model.startsWith("gemini-2.5"))
      toThinkingBudgetConfig(model, effort)
    else {
      logger.warn(
        s"Skipping thinking config for model '$model' - thinking is only supported on Gemini 2.5+ and 3.x models. Reasoning effort '${effort.toString.toLowerCase}' will be ignored."
      )
      None
    }
  }

  private def isGemini3(model: String): Boolean =
    model.startsWith("gemini-3-") || model.startsWith("gemini-3.")

  // Gemini 3 Pro does NOT support MINIMAL (min level is LOW). All Flash variants do.
  private def isGemini3Pro(model: String): Boolean =
    isGemini3(model) && model.contains("-pro") && !model.contains("image")

  private def toThinkingLevelConfig(
    model: String,
    effort: ReasoningEffort
  ): Option[ThinkingConfig] = {
    val pro = isGemini3Pro(model)
    val level: ThinkingLevel = effort match {
      case ReasoningEffort.none | ReasoningEffort.minimal =>
        if (pro) ThinkingLevel.LOW else ThinkingLevel.MINIMAL
      case ReasoningEffort.low    => ThinkingLevel.LOW
      case ReasoningEffort.medium => ThinkingLevel.MEDIUM
      case ReasoningEffort.high | ReasoningEffort.xhigh | ReasoningEffort.max =>
        ThinkingLevel.HIGH
    }

    logger.debug(
      s"Converting reasoning effort '${effort.toString.toLowerCase}' to Gemini thinking level: $level (model: $model)"
    )

    Some(
      ThinkingConfig(
        includeThoughts = Some(false),
        thinkingBudget = None, // mutually exclusive with thinkingLevel on Gemini 3
        thinkingLevel = Some(level)
      )
    )
  }

  private def toThinkingBudgetConfig(
    model: String,
    effort: ReasoningEffort
  ): Option[ThinkingConfig] = {
    import io.cequence.wsclient.ConfigImplicits._
    val effortKey = effort.toString.toLowerCase
    val configPath =
      s"$configPrefix.reasoning-effort-thinking-budget-mapping.$effortKey.gemini"

    clientConfig
      .optionalInt(configPath)
      .map { budget =>
        logger.debug(
          s"Converting reasoning effort '$effortKey' to thinking budget: $budget"
        )

        // budget = 0 is out of range for 2.5 Pro (min 128), so clamp to the minimum.
        // Conversely, 2.5 Flash / Flash-Lite cap thinkingBudget at 24576 (Pro allows up to
        // 32768), so clamp the 'max' effort mapping (32768) down on non-Pro models.
        val nonProMaxBudget = 24576
        val isPro = model.startsWith(NonOpenAIModelId.gemini_2_5_pro)
        val budgetFinal =
          if (budget == 0 && isPro) 128
          else if (!isPro && budget > nonProMaxBudget) {
            logger.warn(
              s"Thinking budget $budget exceeds the maximum of $nonProMaxBudget for model '$model'. Clamping to $nonProMaxBudget."
            )
            nonProMaxBudget
          } else budget

        ThinkingConfig(
          includeThoughts = Some(false),
          thinkingBudget = Some(budgetFinal),
          thinkingLevel = None
        )
      }
      .orElse {
        logger.warn(
          s"No thinking budget mapping found for reasoning effort '$effortKey' in config path: $configPath"
        )
        None
      }
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

    case JsonSchema.Null() =>
      Schema(
        `type` = SchemaType.TYPE_UNSPECIFIED
      )

    case JsonSchema.Object(properties, required, additionalProperties, description) =>
      // additional properties not supported
      if (additionalProperties.nonEmpty && additionalProperties.get)
        logger.warn(
          "Gemini does not support 'additionalProperties' in JSON schema - this field will be ignored"
        )

      val propertiesFinal =
        if (properties.nonEmpty)
          properties
        else
          // GenerateContentRequest.generation_config.response_schema.properties: should be non-empty for OBJECT type
          Seq(
            "_filler_field" -> JsonSchema.String(
              Some(
                "Required field to satisfy Gemini's non-empty object schema requirement. Always output 'none'."
              )
            )
          )

      Schema(
        `type` = SchemaType.OBJECT,
        description = description,
        properties = Some(
          propertiesFinal.map { case (key, jsonSchema) =>
            key -> toGeminiJSONSchema(jsonSchema)
          }.toMap
        ),
        required = Some(required)
      )

    case JsonSchema.Array(items, description) =>
      Schema(
        `type` = SchemaType.ARRAY,
        description = description,
        items = Some(toGeminiJSONSchema(items))
      )

    case _ =>
      throw new OpenAIScalaClientException("Unsupported JSON schema type for Gemini.")
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
      usage = Some(toOpenAIUsage(response.usageMetadata)),
      originalResponse = Some(response)
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
  ): AssistantMessage = {
    val texts = content.parts.collect { case Part.Text(text) => text }
    val hasToolCalls = content.parts.exists {
      case _: Part.FunctionCall => true
      case _                    => false
    }

    if (hasToolCalls)
      logger.warn(
        "Gemini response includes function calls; OpenAI adapter will expose only text content. " +
          "Inspect originalResponse for tool call details."
      )

    AssistantMessage(texts.mkString("\n"))
  }

  private def toOpenAIAssistantChunkMessage(
    content: Content
  ): ChunkMessageSpec = {
    val texts = content.parts.collect { case Part.Text(text) => text }

    ChunkMessageSpec(
      Some(OpenAIChatRole.Assistant),
      if (texts.nonEmpty) Some(texts.mkString("\n")) else None
    )
  }

  private def parseJsonObjectToMap(
    jsonString: String,
    context: String
  ): Map[String, Any] = {
    if (jsonString.trim.isEmpty) {
      Map.empty
    } else {
      try {
        Json.parse(jsonString) match {
          case obj: JsObject =>
            JsonUtil.toValueMap(obj)
          case other =>
            logger.warn(s"Gemini $context is not a JSON object; wrapping as value.")
            JsonUtil.toValueMap(Json.obj("value" -> other))
        }
      } catch {
        case ex: Exception =>
          logger.warn(
            s"Failed to parse Gemini $context as JSON object; sending as raw string.",
            ex
          )
          Map("raw" -> jsonString)
      }
    }
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
      ),
      completion_tokens_details = usageMetadata.thoughtsTokenCount.map { thinkingTokens =>
        CompletionTokenDetails(
          reasoning_tokens = Some(thinkingTokens),
          accepted_prediction_tokens = None,
          rejected_prediction_tokens = None
        )
      }
    )

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse] = {
    val (userMessages, systemMessage) = splitMessage(messages)

    val geminiFunctionDeclarations = tools.collect { case ft: FunctionTool =>
      FunctionDeclaration(
        name = ft.name,
        description = ft.description.getOrElse(""),
        parameters = Some(toGeminiJSONSchema(ft.parameters))
      )
    }

    val geminiTools = Seq(GeminiTool.FunctionDeclarations(geminiFunctionDeclarations))

    val toolConfig = responseToolChoice.map { name =>
      ToolConfig.FunctionCallingConfig(
        mode = Some(FunctionCallingMode.ANY),
        allowedFunctionNames = Some(Seq(name))
      )
    }

    (for {
      baseSettings <- handleCaching(systemMessage, userMessages, settings)
      geminiSettings = baseSettings.copy(
        tools = Some(geminiTools),
        toolConfig = toolConfig.orElse(settings.getGeminiToolConfig)
      )
      response <- underlying.generateContent(
        userMessages.map(toGeminiContent),
        geminiSettings
      )
    } yield toOpenAIToolResponse(response)).recoverWith(repackAsOpenAIException)
  }

  private def toOpenAIToolResponse(
    response: GenerateContentResponse
  ): ChatToolCompletionResponse = {
    val choices = response.candidates.map { candidate =>
      val toolCalls = candidate.content.parts.collect {
        case Part.FunctionCall(id, name, args) =>
          val callId = id.getOrElse(java.util.UUID.randomUUID().toString)
          val argsJson = Json.toJson(args)(JsonUtil.StringAnyMapFormat).toString
          (
            callId,
            FunctionCallSpec(name, argsJson): io.cequence.openaiscala.domain.ToolCallSpec
          )
      }

      val texts = candidate.content.parts.collect { case Part.Text(text) => text }

      val message = AssistantToolMessage(
        content = if (texts.nonEmpty) Some(texts.mkString("\n")) else None,
        name = None,
        tool_calls = toolCalls
      )

      ChatToolCompletionChoiceInfo(
        message = message,
        index = candidate.index.getOrElse(0),
        finish_reason = candidate.finishReason.map(_.toString)
      )
    }

    ChatToolCompletionResponse(
      id = "gemini",
      created = new java.util.Date(),
      model = response.modelVersion,
      system_fingerprint = None,
      choices = choices,
      usage = Some(toOpenAIUsage(response.usageMetadata)),
      originalResponse = Some(response)
    )
  }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
