package io.cequence.openaiscala.vertexai.service

import com.google.cloud.vertexai.api.GenerateContentResponse.UsageMetadata
import com.google.cloud.vertexai.api.{
  Content,
  FileData,
  GenerateContentResponse,
  GenerationConfig,
  Part,
  Schema,
  Type
}
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ChatRole,
  DeveloperMessage,
  ImageURLContent,
  JsonSchema,
  MessageSpec,
  SystemMessage,
  TextContent,
  UserMessage,
  UserSeqMessage
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import org.slf4j.LoggerFactory

import java.{util => ju}
import scala.collection.convert.ImplicitConversions.`iterable asJava`
import scala.collection.convert.ImplicitConversions.`map AsJavaMap`
import scala.collection.convert.ImplicitConversions.`list asScalaBuffer`

package object impl {

  private val logger: Logger = Logger(
    LoggerFactory.getLogger("io.cequence.openaiscala.vertexai.service.impl")
  )

  def toNonSystemVertexAI(messages: Seq[BaseMessage]): Seq[Content] =
    messages.collect {
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
              val encoding = mediaType.takeWhile(_ != ',')
              val data = encodingAndData.drop(encoding.length + 1)

              // TODO: try this
              Part
                .newBuilder()
                .setFileData(
                  FileData.newBuilder().setMimeType(mediaType).setFileUri(data).build()
                )
                .build()
            } else {
              throw new IllegalArgumentException(
                "Image content only supported by providing image data directly. Must start with 'data:'."
              )
            }
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

      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.User =>
        Content
          .newBuilder()
          .setRole("USER")
          .addParts(0, Part.newBuilder().setText(content).build())
          .build()

      case x: BaseMessage =>
        throw new OpenAIScalaClientException(
          s"Unsupported message type: ${x.getClass().getName()}"
        )
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
    // If specified, top-k sampling will be used.
    setValue(
      _.setTopK(_: Float),
      if (settings.logprobs.getOrElse(false)) settings.top_logprobs.map(_.toFloat) else None
    )
    //  Number of candidates to generate.
    setValue(_.setCandidateCount(_: Int), settings.n)

    // The maximum number of output tokens to generate per message
    setValue(_.setMaxOutputTokens(_: Int), settings.max_tokens)

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

      case JsonSchema.Object(properties, required, additionalProperties) =>
        // additional properties not supported
        if (additionalProperties.nonEmpty && additionalProperties.get)
          logger.warn(
            "VertexAI does not support 'additionalProperties' in JSON schema - this field will be ignored"
          )

        val b = builder.setType(Type.OBJECT)
        if (properties.nonEmpty) {
          val propsMap = properties.map { case (key, jsonSchema) =>
            key -> toVertexJSONSchema(jsonSchema)
          }.toMap

          b.putAllProperties(`map AsJavaMap`(propsMap))
        }

        if (required.nonEmpty) {
          b.addAllRequired(`iterable asJava`(required))
        }

      case JsonSchema.Array(items) =>
        val b = builder.setType(Type.ARRAY)
        b.setItems(toVertexJSONSchema(items))

      case _ =>
        throw new OpenAIScalaClientException(
          s"Unsupported JSON schema type for Google Vertex."
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

  def toOpenAIAssistantMessage(content: Content): AssistantMessage = {
    val textContents = content.getPartsList.map { part =>
      // TODO: check different types
      part.getText
    }

    AssistantMessage(textContents.mkString("\n"), name = None)
  }

  def toOpenAI(usageInfo: UsageMetadata): OpenAIUsageInfo = {
    OpenAIUsageInfo(
      prompt_tokens = usageInfo.getPromptTokenCount,
      total_tokens = usageInfo.getTotalTokenCount,
      completion_tokens = Some(usageInfo.getCandidatesTokenCount)
      // prompt_tokens_details = Some(
      //   PromptTokensDetails(
      //     cached_tokens = usageInfo.getCachedContentTokenCount.getOrElse(0),
      //     audio_tokens = None
      //   )
      // )
    )
  }
}
