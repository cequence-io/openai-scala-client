package io.cequence.openaiscala.vertexai.service

import com.google.cloud.vertexai.api.GenerateContentResponse.UsageMetadata
import com.google.cloud.vertexai.api.{
  Blob,
  Content,
  FunctionCall,
  FunctionCallingConfig,
  FunctionDeclaration => VertexFunctionDeclaration,
  FunctionResponse,
  GenerateContentResponse,
  GenerationConfig,
  Part,
  Schema,
  Tool => VertexTool,
  ToolConfig => VertexToolConfig,
  Type
}
import com.google.api.gax.rpc.{
  ApiException,
  DeadlineExceededException,
  InternalException,
  InvalidArgumentException,
  PermissionDeniedException,
  ResourceExhaustedException,
  UnauthenticatedException,
  UnavailableException
}
import com.google.protobuf.{ByteString, Struct, Value}
import com.google.protobuf.util.JsonFormat
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaClientTimeoutException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaServerErrorException,
  OpenAIScalaUnauthorizedException
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantToolMessage,
  BaseMessage,
  ChatRole,
  DeveloperMessage,
  FileContent,
  FunctionCallSpec,
  ImageURLContent,
  JsonSchema,
  MessageSpec,
  SystemMessage,
  TextContent,
  ToolMessage,
  UserMessage,
  UserSeqMessage
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  CompletionTokenDetails,
  PromptTokensDetails,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.vertexai.domain.{
  FunctionDeclaration => VertexAIFunctionDeclaration,
  Schema => VertexAISchema,
  SchemaType => VertexAISchemaType,
  Tool => VertexAITool
}
import io.cequence.openaiscala.vertexai.domain.settings.{
  FunctionCallingMode,
  ToolConfig,
  CreateChatCompletionSettingsOps
}
import CreateChatCompletionSettingsOps._
import org.slf4j.LoggerFactory

import java.{util => ju}
import java.util.concurrent.{CompletionException, ExecutionException}
import scala.collection.convert.ImplicitConversions.`iterable asJava`
import scala.collection.convert.ImplicitConversions.`map AsJavaMap`
import scala.collection.convert.ImplicitConversions.`list asScalaBuffer`

package object impl extends io.cequence.openaiscala.service.HasOpenAIConfig {

  private val logger: Logger = Logger(
    LoggerFactory.getLogger("io.cequence.openaiscala.vertexai.service.impl")
  )

  def toNonSystemVertexAI(messages: Seq[BaseMessage]): Seq[Content] =
    mergeConsecutiveFunctionResponses(messages.collect {
      case UserMessage(content, _) =>
        Content
          .newBuilder()
          .setRole("USER")
          .addParts(0, Part.newBuilder().setText(content).build())
          .build()

      case UserSeqMessage(contents, _) =>
        val parts = contents.map {
          case TextContent(text) =>
            Part.newBuilder().setText(text).build()

          case ImageURLContent(url) =>
            if (url.startsWith("data:")) {
              val mediaTypeEncodingAndData = url.drop(5)
              val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
              val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
              val encoding = encodingAndData.takeWhile(_ != ',')
              val data = encodingAndData.drop(encoding.length + 1)

              if (encoding != "base64") {
                throw new IllegalArgumentException(
                  s"ImageURLContent for Vertex AI: only base64-encoded data URLs are supported, got '$encoding'."
                )
              }

              Part
                .newBuilder()
                .setInlineData(
                  Blob
                    .newBuilder()
                    .setMimeType(mediaType)
                    .setData(ByteString.copyFrom(ju.Base64.getDecoder.decode(data)))
                    .build()
                )
                .build()
            } else {
              throw new IllegalArgumentException(
                "Image content only supported by providing image data directly. Must start with 'data:'."
              )
            }

          case FileContent(_, Some(fileData), _) if fileData.startsWith("data:") =>
            val mediaTypeEncodingAndData = fileData.drop(5)
            val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
            val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
            val encoding = encodingAndData.takeWhile(_ != ',')
            val data = encodingAndData.drop(encoding.length + 1)

            if (encoding != "base64") {
              throw new IllegalArgumentException(
                s"FileContent for Vertex AI: only base64-encoded data URLs are supported, got '$encoding'."
              )
            }

            Part
              .newBuilder()
              .setInlineData(
                Blob
                  .newBuilder()
                  .setMimeType(mediaType)
                  .setData(ByteString.copyFrom(ju.Base64.getDecoder.decode(data)))
                  .build()
              )
              .build()

          case _: FileContent =>
            throw new IllegalArgumentException(
              "FileContent for Vertex AI: only base64 fileData as a data URL is supported " +
                "(e.g. data:application/pdf;base64,...). OpenAI file_id is not portable."
            )
        }

        val contentBuilder = Content.newBuilder().setRole("USER")

        parts.zipWithIndex.foreach { case (part, index) =>
          contentBuilder.addParts(index, part)
        }

        contentBuilder.build()

      case AssistantMessage(content, _, _) =>
        Content
          .newBuilder()
          .setRole("MODEL")
          .addParts(0, Part.newBuilder().setText(content).build())
          .build()

      case AssistantToolMessage(content, _, toolCalls) =>
        val textPart =
          content.filter(_.nonEmpty).map(text => Part.newBuilder().setText(text).build())

        val functionCallParts = toolCalls.map { case (_, callSpec) =>
          val (name, argumentsJson) = callSpec match {
            case FunctionCallSpec(name, arguments) => (name, arguments)
          }

          Part
            .newBuilder()
            .setFunctionCall(
              FunctionCall
                .newBuilder()
                .setName(name)
                .setArgs(argsToStruct(argumentsJson))
                .build()
            )
            .build()
        }

        val contentBuilder = Content.newBuilder().setRole("MODEL")

        (textPart.toSeq ++ functionCallParts).zipWithIndex.foreach { case (part, index) =>
          contentBuilder.addParts(index, part)
        }

        contentBuilder.build()

      // Vertex AI has no notion of tool_call_id - a function response is matched back to its
      // call by function name alone, so `toolCallId` is intentionally not used here.
      case ToolMessage(content, _, name) =>
        Content
          .newBuilder()
          .setRole("USER")
          .addParts(
            0,
            Part
              .newBuilder()
              .setFunctionResponse(
                FunctionResponse
                  .newBuilder()
                  .setName(name)
                  .setResponse(toolResponseToStruct(content))
                  .build()
              )
              .build()
          )
          .build()

      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.User =>
        Content
          .newBuilder()
          .setRole("USER")
          .addParts(0, Part.newBuilder().setText(content).build())
          .build()

      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.Assistant =>
        Content
          .newBuilder()
          .setRole("MODEL")
          .addParts(0, Part.newBuilder().setText(content).build())
          .build()

      // Skip system/developer messages - they are handled separately by toSystemVertexAI
    })

  // Parses a function call's OpenAI-style JSON arguments string into a proto Struct. An
  // empty/blank or otherwise invalid JSON string yields an empty Struct (with a warning)
  // rather than failing the whole request.
  private def argsToStruct(argumentsJson: String): Struct = {
    if (argumentsJson == null || argumentsJson.trim.isEmpty) {
      Struct.newBuilder().build()
    } else {
      try {
        val builder = Struct.newBuilder()
        JsonFormat.parser().merge(argumentsJson, builder)
        builder.build()
      } catch {
        case e: Exception =>
          logger.warn(
            s"Failed to parse function call arguments as JSON: '$argumentsJson' - using an empty Struct instead. Error: ${e.getMessage}"
          )
          Struct.newBuilder().build()
      }
    }
  }

  // Converts a ToolMessage's content into the Struct expected by FunctionResponse#setResponse.
  // If the content parses as a JSON object, that object is used as-is; otherwise the raw
  // string (or an empty string when absent) is wrapped under a single "result" field.
  private def toolResponseToStruct(content: Option[String]): Struct = {
    val raw = content.getOrElse("")

    val asObjectStruct =
      if (raw.trim.startsWith("{")) {
        try {
          val builder = Struct.newBuilder()
          JsonFormat.parser().merge(raw, builder)
          Some(builder.build())
        } catch {
          case _: Exception => None
        }
      } else None

    asObjectStruct.getOrElse(
      Struct
        .newBuilder()
        .putFields("result", Value.newBuilder().setStringValue(raw).build())
        .build()
    )
  }

  // Gemini/Vertex require all function responses belonging to one turn (e.g. parallel tool
  // calls) to be sent as parts of a SINGLE "USER" content. ToolMessage is converted 1:1 above,
  // so adjacent function-response-only "USER" contents are merged here as a post-processing
  // step.
  private def mergeConsecutiveFunctionResponses(contents: Seq[Content]): Seq[Content] = {
    def isFunctionResponseOnly(content: Content): Boolean =
      content.getRole == "USER" &&
        content.getPartsCount > 0 &&
        content.getPartsList.toSeq.forall(_.hasFunctionResponse)

    contents.foldLeft(Vector.empty[Content]) {
      (
        acc,
        content
      ) =>
        acc.lastOption match {
          case Some(last) if isFunctionResponseOnly(last) && isFunctionResponseOnly(content) =>
            val merged = last.toBuilder.addAllParts(content.getPartsList).build()
            acc.dropRight(1) :+ merged

          case _ =>
            acc :+ content
        }
    }
  }

  def toSystemVertexAI(
    messages: Seq[BaseMessage]
  ): Option[Content] = {
    val contents = messages.collect {
      case SystemMessage(content, _)    => content
      case DeveloperMessage(content, _) => content
      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.System =>
        content
    }

    if (contents.nonEmpty) {
      val parts = contents.map { content =>
        Part.newBuilder().setText(content).build()
      }

      val builder = Content.newBuilder().setRole("SYSTEM")

      parts.zipWithIndex.foreach { case (part, index) =>
        builder.addParts(index, part)
      }
      Some(builder.build())
    } else None
  }

  /**
   * Converts OpenAI's reasoning_effort to a thinking budget for the proto
   * `GenerationConfig.ThinkingConfig`, mirroring the Gemini-direct adapter's
   * `toThinkingBudgetConfig` (same config mapping, same 2.5-family clamps).
   *
   * Limitation: the proto-based `com.google.cloud.vertexai.api` SDK only exposes
   * `setThinkingBudget`; `thinkingLevel` (MINIMAL/LOW/MEDIUM/HIGH, required by Gemini 3.x) is
   * only available in the unified `com.google.genai` SDK - reasoning_effort on Gemini 3.x
   * models is therefore skipped with a warning until this path switches SDKs.
   */
  private def toThinkingBudget(
    model: String,
    reasoningEffort: Option[io.cequence.openaiscala.domain.settings.ReasoningEffort]
  ): Option[Int] = reasoningEffort.flatMap { effort =>
    import io.cequence.wsclient.ConfigImplicits._

    // batch paths may carry the full publisher resource name - branch on the bare model id
    val modelId = model.split('/').last

    if (modelId.startsWith("gemini-3-") || modelId.startsWith("gemini-3.")) {
      logger.warn(
        s"Skipping reasoning_effort '${effort.toString.toLowerCase}' for model '$model' - Gemini 3.x needs thinkingLevel, which the proto-based VertexAI SDK cannot express (use the Gemini-direct client, or wait for the google-genai SDK switch)."
      )
      None
    } else if (modelId.startsWith("gemini-2.5")) {
      val effortKey = effort.toString.toLowerCase
      val configPath =
        s"$configPrefix.reasoning-effort-thinking-budget-mapping.$effortKey.gemini"

      clientConfig.optionalInt(configPath) match {
        case Some(budget) =>
          // same clamps as the Gemini-direct adapter: 2.5 Pro floor 128, Flash-Lite
          // non-zero floor 512, non-Pro cap 24576
          val nonProMaxBudget = 24576
          val flashLiteMinBudget = 512
          val isPro = modelId.startsWith("gemini-2.5-pro")
          val isFlashLite = modelId.startsWith("gemini-2.5-flash-lite")
          val budgetFinal =
            if (budget == 0 && isPro) 128
            else if (isFlashLite && budget > 0 && budget < flashLiteMinBudget) {
              logger.warn(
                s"Thinking budget $budget is below the minimum of $flashLiteMinBudget for model '$model'. Clamping to $flashLiteMinBudget."
              )
              flashLiteMinBudget
            } else if (!isPro && budget > nonProMaxBudget) {
              logger.warn(
                s"Thinking budget $budget exceeds the maximum of $nonProMaxBudget for model '$model'. Clamping to $nonProMaxBudget."
              )
              nonProMaxBudget
            } else budget

          Some(budgetFinal)

        case None =>
          logger.warn(
            s"No thinking budget mapping found for reasoning effort '$effortKey' in config path: $configPath"
          )
          None
      }
    } else {
      logger.warn(
        s"Skipping reasoning_effort '${effort.toString.toLowerCase}' for model '$model' - thinking is only supported on Gemini 2.5+ models on this path."
      )
      None
    }
  }

  def toVertexAI(
    settings: CreateChatCompletionSettings
  ): GenerationConfig = {
    val configBuilder = GenerationConfig.newBuilder()

    def setValue[T](
      setter: (GenerationConfig.Builder, T) => GenerationConfig.Builder,
      value: Option[T]
    ): GenerationConfig.Builder =
      value.map(setter(configBuilder, _)).getOrElse(configBuilder)

    setValue(
      _.setTemperature(_: Float),
      settings.temperature.map(_.toFloat)
    )
    //  If specified, nucleus sampling will be used.
    setValue(
      _.setTopP(_: Float),
      settings.top_p.map(_.toFloat)
    )
    // Positive penalties
    setValue(
      _.setPresencePenalty(_: Float),
      settings.presence_penalty.map(_.toFloat)
    )
    // Frequency penalties.
    setValue(
      _.setFrequencyPenalty(_: Float),
      settings.frequency_penalty.map(_.toFloat)
    )
    // Whether to return the log probabilities of the output tokens.
    setValue(
      _.setResponseLogprobs(_: Boolean),
      settings.logprobs
    )
    // Number of top candidate tokens (per position) to return log probabilities for - only
    // meaningful when logprobs is enabled. Note: there is no OpenAI equivalent for Vertex's
    // top-k SAMPLING parameter, so it is deliberately never set here.
    setValue(
      _.setLogprobs(_: Int),
      if (settings.logprobs.getOrElse(false)) settings.top_logprobs else None
    )
    //  Number of candidates to generate.
    setValue(_.setCandidateCount(_: Int), settings.n)

    // The maximum number of output tokens to generate per message
    setValue(_.setMaxOutputTokens(_: Int), settings.max_tokens)

    // Seed for deterministic sampling, if supported by the model.
    setValue(_.setSeed(_: Int), settings.seed)

    // reasoning_effort -> thinking budget (Gemini 2.5 family; see toThinkingBudget)
    toThinkingBudget(settings.model, settings.reasoning_effort).foreach { budget =>
      configBuilder.setThinkingConfig(
        GenerationConfig.ThinkingConfig.newBuilder().setThinkingBudget(budget)
      )
    }

    // Stop sequences.
    setValue(
      _.addAllStopSequences(_: java.lang.Iterable[String]),
      if (settings.stop.nonEmpty) Some(`iterable asJava`(settings.stop)) else None
    )

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
                "OpenAI's 'strict' mode is not supported by VertexAI. The schema will be used without strict validation. Note: VertexAI does not support 'additionalProperties'."
              )

            Some(toVertexJSONSchema(schema))

          case Right(_) =>
            logger.warn(
              "Map-like legacy JSON schema format is not supported for VertexAI - only structured JsonSchema objects are supported"
            )
            None
        }
      } else
        None

    jsonSchema.foreach { schema =>
      configBuilder.setResponseSchema(schema)
      configBuilder.setResponseMimeType("application/json")
    }

    configBuilder.build()
  }

  private def toVertexJSONSchema(
    jsonSchema: JsonSchema
  ): Schema = {
    val builder = Schema.newBuilder()

    jsonSchema match {
      case JsonSchema.String(description, enumVals) =>
        builder.setType(Type.STRING)
        description.foreach(builder.setDescription)
        enumVals.foreach(builder.addEnum)

      case JsonSchema.Number(description) =>
        val b = builder.setType(Type.NUMBER)
        description.foreach(b.setDescription)

      case JsonSchema.Integer(description) =>
        val b = builder.setType(Type.INTEGER)
        description.foreach(b.setDescription)

      case JsonSchema.Boolean(description) =>
        val b = builder.setType(Type.BOOLEAN)
        description.foreach(b.setDescription)

      case JsonSchema.Null() =>
        builder.setType(Type.TYPE_UNSPECIFIED)

      case JsonSchema.Object(properties, required, additionalProperties, description) =>
        // additional properties not supported
        if (additionalProperties.nonEmpty && additionalProperties.get)
          logger.warn(
            "VertexAI does not support 'additionalProperties' in JSON schema - this field will be ignored"
          )

        val b = builder.setType(Type.OBJECT)
        description.foreach(b.setDescription)
        if (properties.nonEmpty) {
          val propsMap = properties.map { case (key, jsonSchema) =>
            key -> toVertexJSONSchema(jsonSchema)
          }.toMap

          b.putAllProperties(`map AsJavaMap`(propsMap))
        }

        if (required.nonEmpty) {
          b.addAllRequired(`iterable asJava`(required))
        }

      case JsonSchema.Array(items, description) =>
        val b = builder.setType(Type.ARRAY)
        description.foreach(b.setDescription)
        b.setItems(toVertexJSONSchema(items))

      case _ =>
        throw new OpenAIScalaClientException(
          "Unsupported JSON schema type for Google Vertex."
        )
    }

    builder.build()
  }

  def toOpenAI(
    response: GenerateContentResponse,
    model: String
  ): ChatCompletionResponse =
    ChatCompletionResponse(
      id = "vertexai",
      created = new ju.Date(),
      model = model,
      system_fingerprint = None,
      choices = response.getCandidatesList.toSeq.map { candidate =>
        ChatCompletionChoiceInfo(
          index = candidate.getIndex,
          message = toOpenAIAssistantMessage(candidate.getContent),
          finish_reason = Some(candidate.getFinishReason.toString()),
          logprobs = None
        )
      },
      usage = Some(toOpenAI(response.getUsageMetadata)),
      originalResponse = Some(response)
    )

  def toOpenAI(usageInfo: UsageMetadata): OpenAIUsageInfo = {
    val thoughtsTokens = usageInfo.getThoughtsTokenCount

    OpenAIUsageInfo(
      prompt_tokens = usageInfo.getPromptTokenCount,
      total_tokens = usageInfo.getTotalTokenCount,
      // OpenAI semantics: reasoning tokens are INCLUDED in completion_tokens (and broken out
      // in the details) - keeps prompt + completion == total with thinking enabled
      completion_tokens = Some(usageInfo.getCandidatesTokenCount + thoughtsTokens),
      prompt_tokens_details = Some(
        PromptTokensDetails(
          cached_tokens = usageInfo.getCachedContentTokenCount,
          audio_tokens = None
        )
      ),
      completion_tokens_details =
        if (thoughtsTokens > 0)
          Some(CompletionTokenDetails(reasoning_tokens = Some(thoughtsTokens)))
        else None
    )
  }

  // Tool conversion functions

  def toVertexAITools(
    settings: CreateChatCompletionSettings
  ): Option[Seq[VertexTool]] =
    settings.getVertexAITools.map(_.map(toVertexAIToolInternal))

  def toVertexAIToolConfig(
    settings: CreateChatCompletionSettings
  ): Option[VertexToolConfig] =
    settings.getVertexAIToolConfig.map(toVertexAIToolConfigInternal)

  private def toVertexAIToolInternal(tool: VertexAITool): VertexTool = {
    val builder = VertexTool.newBuilder()

    tool match {
      case VertexAITool.FunctionDeclarations(functionDeclarations) =>
        functionDeclarations.foreach { fd =>
          builder.addFunctionDeclarations(toVertexFunctionDeclaration(fd))
        }

      case VertexAITool.GoogleSearch =>
        builder.setGoogleSearch(
          VertexTool.GoogleSearch.newBuilder().build()
        )

      case VertexAITool.CodeExecution =>
        builder.setCodeExecution(
          VertexTool.CodeExecution.newBuilder().build()
        )
    }

    builder.build()
  }

  private def toVertexFunctionDeclaration(
    fd: VertexAIFunctionDeclaration
  ): VertexFunctionDeclaration = {
    val builder =
      VertexFunctionDeclaration.newBuilder().setName(fd.name).setDescription(fd.description)

    fd.parameters.foreach { schema =>
      builder.setParameters(toVertexSchema(schema))
    }

    builder.build()
  }

  private def toVertexSchema(schema: VertexAISchema): Schema = {
    val builder = Schema.newBuilder()

    builder.setType(schema.`type` match {
      case VertexAISchemaType.TYPE_UNSPECIFIED => Type.TYPE_UNSPECIFIED
      case VertexAISchemaType.STRING           => Type.STRING
      case VertexAISchemaType.NUMBER           => Type.NUMBER
      case VertexAISchemaType.INTEGER          => Type.INTEGER
      case VertexAISchemaType.BOOLEAN          => Type.BOOLEAN
      case VertexAISchemaType.ARRAY            => Type.ARRAY
      case VertexAISchemaType.OBJECT           => Type.OBJECT
    })

    schema.format.foreach(builder.setFormat)
    schema.description.foreach(builder.setDescription)
    schema.nullable.foreach(builder.setNullable)
    schema.`enum`.foreach(_.foreach(builder.addEnum))

    schema.properties.foreach { props =>
      val propsMap = props.map { case (key, s) =>
        key -> toVertexSchema(s)
      }.toMap
      builder.putAllProperties(`map AsJavaMap`(propsMap))
    }

    schema.required.foreach { reqs =>
      builder.addAllRequired(`iterable asJava`(reqs))
    }

    schema.items.foreach { items =>
      builder.setItems(toVertexSchema(items))
    }

    builder.build()
  }

  private def toVertexAIToolConfigInternal(toolConfig: ToolConfig): VertexToolConfig = {
    val builder = VertexToolConfig.newBuilder()

    toolConfig match {
      case ToolConfig.FunctionCallingConfig(mode, allowedFunctionNames) =>
        val fcBuilder = FunctionCallingConfig.newBuilder()

        mode.foreach {
          case FunctionCallingMode.MODE_UNSPECIFIED =>
            fcBuilder.setMode(FunctionCallingConfig.Mode.MODE_UNSPECIFIED)
          case FunctionCallingMode.AUTO =>
            fcBuilder.setMode(FunctionCallingConfig.Mode.AUTO)
          case FunctionCallingMode.ANY =>
            fcBuilder.setMode(FunctionCallingConfig.Mode.ANY)
          case FunctionCallingMode.NONE =>
            fcBuilder.setMode(FunctionCallingConfig.Mode.NONE)
        }

        allowedFunctionNames.foreach { names =>
          fcBuilder.addAllAllowedFunctionNames(`iterable asJava`(names))
        }

        builder.setFunctionCallingConfig(fcBuilder.build())
    }

    builder.build()
  }

  def toOpenAIAssistantMessage(content: Content): AssistantMessage = {
    val parts = content.getPartsList.toSeq

    // Check if there are function calls
    val functionCalls = parts.filter(_.hasFunctionCall)

    if (functionCalls.nonEmpty) {
      // Format function calls as part of the content
      val functionCallsText = functionCalls.zipWithIndex.map { case (part, index) =>
        val fc = part.getFunctionCall
        val argsJson = com.google.protobuf.util.JsonFormat.printer().print(fc.getArgs)
        s"[Function Call ${index + 1}] ${fc.getName}: $argsJson"
      }.mkString("\n")

      // Get any text content
      val textContent =
        parts.filter(p => p.hasText && p.getText.nonEmpty).map(_.getText).mkString("\n")

      val fullContent =
        if (textContent.nonEmpty) s"$textContent\n\n$functionCallsText"
        else functionCallsText

      AssistantMessage(fullContent, name = None)
    } else {
      // Just text content
      val textContents = parts.filter(_.hasText).map(_.getText)
      AssistantMessage(textContents.mkString("\n"), name = None)
    }
  }

  // -- Exception repacking (Vertex/gax -> OpenAI exceptions) --

  /**
   * Unwraps `CompletionException`/`ExecutionException` (recursively, following non-null
   * causes) and maps the underlying gax `ApiException` subtypes to their OpenAI equivalents,
   * so callers of this module can pattern-match on the usual `OpenAIScalaClientException`
   * hierarchy regardless of the Vertex AI transport in use. An already-
   * `OpenAIScalaClientException` passes through unchanged; anything unrecognized is returned
   * as-is.
   */
  def toOpenAIException: PartialFunction[Throwable, Throwable] = {
    case e: OpenAIScalaClientException => e

    case e: CompletionException if e.getCause != null => toOpenAIException(e.getCause)
    case e: ExecutionException if e.getCause != null  => toOpenAIException(e.getCause)

    case e: ResourceExhaustedException =>
      new OpenAIScalaRateLimitException(e.getMessage, e)

    case e: UnavailableException =>
      new OpenAIScalaEngineOverloadedException(e.getMessage, e)

    case e: DeadlineExceededException =>
      new OpenAIScalaClientTimeoutException(e.getMessage, e)

    case e: InternalException =>
      new OpenAIScalaServerErrorException(e.getMessage, e)

    case e: UnauthenticatedException =>
      new OpenAIScalaUnauthorizedException(e.getMessage, e)

    case e: PermissionDeniedException =>
      new OpenAIScalaUnauthorizedException(e.getMessage, e)

    case e: InvalidArgumentException =>
      new OpenAIScalaClientException(e.getMessage, e)

    case e: ApiException =>
      new OpenAIScalaClientException(e.getMessage, e)

    case e => e
  }

  def repackAsOpenAIException[T]: PartialFunction[Throwable, scala.concurrent.Future[T]] = {
    case e => scala.concurrent.Future.failed(toOpenAIException(e))
  }
}
