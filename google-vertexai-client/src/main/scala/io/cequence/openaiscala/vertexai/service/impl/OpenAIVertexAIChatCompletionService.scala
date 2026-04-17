package io.cequence.openaiscala.vertexai.service.impl

import akka.NotUsed
import akka.stream.scaladsl.{Source, StreamConverters}
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.GenerateContentResponse
import com.google.cloud.vertexai.generativeai.GenerativeModel
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionTool,
  ChatRole,
  FunctionCallSpec,
  JsonSchema
}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse,
  ChunkMessageSpec
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
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

import java.util.concurrent.CompletableFuture
import scala.collection.convert.ImplicitConversions.`list asScalaBuffer`
import scala.collection.convert.ImplicitConversions.`seq AsJavaList`
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

// TODO: convert Google exceptions
//  (e.g. java.util.concurrent.CompletionException (ResourceExhaustedException)) to OpenAI exceptions
private[service] class OpenAIVertexAIChatCompletionService(
  underlying: VertexAI
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val model = createModel(messages, settings)

    val javaFuture = model.generateContentAsync(
      toNonSystemVertexAI(
        messages.filter(message =>
          message.role != ChatRole.System && message.role != ChatRole.Developer
        )
      )
    )
    val scalaFuture: Future[GenerateContentResponse] =
      toScala(CompletableFuture.supplyAsync(() => javaFuture.get))

    scalaFuture.map { response =>
      toOpenAI(response, settings.model)
    }
  }

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] = {
    val functionDeclarations = tools.collect { case ft: FunctionTool =>
      VertexAIFunctionDeclaration(
        name = ft.name,
        description = ft.description.getOrElse(""),
        parameters = Some(toVertexAISchema(ft.parameters))
      )
    }

    val toolConfig = responseToolChoice match {
      case Some(name) =>
        Some(
          ToolConfig.FunctionCallingConfig(
            mode = Some(FunctionCallingMode.ANY),
            allowedFunctionNames = Some(Seq(name))
          )
        )
      case None =>
        Some(
          ToolConfig.FunctionCallingConfig(
            mode = Some(FunctionCallingMode.AUTO),
            allowedFunctionNames = None
          )
        )
    }

    val settingsWithTools =
      settings.setVertexAITools(Seq(VertexAITool.FunctionDeclarations(functionDeclarations)))

    val settingsWithToolConfig =
      toolConfig.map(settingsWithTools.setVertexAIToolConfig).getOrElse(settingsWithTools)

    val model = createModel(messages, settingsWithToolConfig)

    val javaFuture = model.generateContentAsync(
      toNonSystemVertexAI(
        messages.filter(message =>
          message.role != ChatRole.System && message.role != ChatRole.Developer
        )
      )
    )
    val scalaFuture: Future[GenerateContentResponse] =
      toScala(CompletableFuture.supplyAsync(() => javaFuture.get))

    scalaFuture.map { response =>
      toOpenAIToolResponse(response, settings.model)
    }
  }

  private def toVertexAISchema(jsonSchema: JsonSchema): VertexAISchema =
    jsonSchema match {
      case JsonSchema.String(description, enumVals) =>
        VertexAISchema(
          `type` = VertexAISchemaType.STRING,
          description = description,
          `enum` = if (enumVals.nonEmpty) Some(enumVals) else None
        )

      case JsonSchema.Number(description) =>
        VertexAISchema(`type` = VertexAISchemaType.NUMBER, description = description)

      case JsonSchema.Integer(description) =>
        VertexAISchema(`type` = VertexAISchemaType.INTEGER, description = description)

      case JsonSchema.Boolean(description) =>
        VertexAISchema(`type` = VertexAISchemaType.BOOLEAN, description = description)

      case JsonSchema.Null() =>
        VertexAISchema(`type` = VertexAISchemaType.TYPE_UNSPECIFIED)

      case JsonSchema.Object(properties, required, _, description) =>
        VertexAISchema(
          `type` = VertexAISchemaType.OBJECT,
          description = description,
          properties =
            if (properties.nonEmpty)
              Some(properties.map { case (k, v) => k -> toVertexAISchema(v) }.toMap)
            else None,
          required = if (required.nonEmpty) Some(required) else None
        )

      case JsonSchema.Array(items, description) =>
        VertexAISchema(
          `type` = VertexAISchemaType.ARRAY,
          description = description,
          items = Some(toVertexAISchema(items))
        )

      case _ =>
        VertexAISchema(`type` = VertexAISchemaType.TYPE_UNSPECIFIED)
    }

  private def toOpenAIToolResponse(
    response: GenerateContentResponse,
    model: String
  ): ChatToolCompletionResponse = {
    val candidates = response.getCandidatesList.toSeq

    val choices = candidates.map { candidate =>
      val parts = candidate.getContent.getPartsList.toSeq

      val toolCalls = parts.filter(_.hasFunctionCall).map { part =>
        val fc = part.getFunctionCall
        val argsJson = com.google.protobuf.util.JsonFormat.printer().print(fc.getArgs)
        // Vertex FunctionCall has no server-assigned id, so synthesize a unique one
        // (function name alone collides on parallel calls to the same function).
        val callId = java.util.UUID.randomUUID().toString
        (
          callId,
          FunctionCallSpec(fc.getName, argsJson): io.cequence.openaiscala.domain.ToolCallSpec
        )
      }

      val texts = parts.filter(p => p.hasText && p.getText.nonEmpty).map(_.getText)

      val message = AssistantToolMessage(
        content = if (texts.nonEmpty) Some(texts.mkString("\n")) else None,
        name = None,
        tool_calls = toolCalls
      )

      ChatToolCompletionChoiceInfo(
        message = message,
        index = candidate.getIndex,
        finish_reason = Option(candidate.getFinishReason).map(_.toString)
      )
    }

    ChatToolCompletionResponse(
      id = "vertexai",
      created = new java.util.Date(),
      model = model,
      system_fingerprint = None,
      choices = choices,
      usage = Some(toOpenAI(response.getUsageMetadata))
    )
  }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] = {
    val model = createModel(messages, settings)

    val javaStream = model.generateContentStream(
      toNonSystemVertexAI(
        messages.filter(message =>
          message.role != ChatRole.System && message.role != ChatRole.Developer
        )
      )
    )
    val scalaStream = StreamConverters.fromJavaStream(() => javaStream.stream())

    scalaStream.map { response =>
      val openAIResponse = toOpenAI(response, settings.model)

      ChatCompletionChunkResponse(
        id = openAIResponse.id,
        created = openAIResponse.created,
        model = openAIResponse.model,
        system_fingerprint = openAIResponse.system_fingerprint,
        choices = openAIResponse.choices.map { info =>
          ChatCompletionChoiceChunkInfo(
            delta = ChunkMessageSpec(
              Some(ChatRole.Assistant),
              Some(info.message.content)
            ),
            index = info.index,
            finish_reason = info.finish_reason
          )
        },
        usage = openAIResponse.usage
      )
    }
  }

  private def createModel(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): GenerativeModel = {
    val config = toVertexAI(settings)

    var modelAux = new GenerativeModel(settings.model, underlying).withGenerationConfig(config)

    // Add tools if present
    toVertexAITools(settings).foreach { tools =>
      modelAux = modelAux.withTools(`seq AsJavaList`(tools))
    }

    // Add tool config if present
    toVertexAIToolConfig(settings).foreach { toolConfig =>
      modelAux = modelAux.withToolConfig(toolConfig)
    }

    // TODO: system messages not support e.g. for gemini-1.0-pro-001
    toSystemVertexAI(messages)
      .map(
        modelAux.withSystemInstruction
      )
      .getOrElse(
        modelAux
      )
  }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
