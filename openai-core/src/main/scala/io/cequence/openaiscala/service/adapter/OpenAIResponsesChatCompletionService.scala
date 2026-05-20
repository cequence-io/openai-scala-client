package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse,
  CompletionTokenDetails,
  PromptTokensDetails,
  UsageInfo => ChatUsageInfo
}
import io.cequence.openaiscala.domain.responsesapi._
import io.cequence.openaiscala.domain.responsesapi.OutputMessageContent.{OutputText, Refusal}
import io.cequence.openaiscala.domain.responsesapi.tools.{
  FunctionToolCall,
  FunctionToolCallOutput,
  FunctionToolOutput,
  ToolChoice,
  FunctionTool => ResponsesFunctionTool
}
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIResponsesService}
import io.cequence.wsclient.service.CloseableService
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAIResponsesChatCompletionService(
  underlying: OpenAIResponsesService with CloseableService
)(
  implicit ec: ExecutionContext
) extends OpenAIChatCompletionService {

  private val logger = LoggerFactory.getLogger(getClass)

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val (instructions, items) = convertMessages(messages)
    val responsesSettings =
      toResponsesSettings(settings, instructions, tools = Nil, toolChoice = None)

    underlying
      .createModelResponse(Inputs.Items(items: _*), responsesSettings)
      .map(toOpenAIChatCompletionResponse)
  }

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] = {
    val (instructions, items) = convertMessages(messages)

    val responsesTools = tools.collect { case ft: AssistantTool.FunctionTool =>
      ResponsesFunctionTool(
        ft.name,
        ft.parameters,
        ft.strict.getOrElse(false),
        ft.description
      )
    }

    val responsesToolChoice = responseToolChoice match {
      case None       => Some(ToolChoice.Mode.Auto)
      case Some(name) => Some(ToolChoice.FunctionTool(name))
    }

    val responsesSettings =
      toResponsesSettings(settings, instructions, responsesTools, responsesToolChoice)

    underlying
      .createModelResponse(Inputs.Items(items: _*), responsesSettings)
      .map(toOpenAIToolCompletionResponse)
  }

  private def convertMessages(
    messages: Seq[BaseMessage]
  ): (Option[String], Seq[Input]) = {
    val (instructionMessages, nonSystemMessages) = messages.partition {
      case _: SystemMessage | _: DeveloperMessage => true
      case _                                      => false
    }

    val instructions = {
      val texts = instructionMessages.collect {
        case SystemMessage(content, _)    => content
        case DeveloperMessage(content, _) => content
      }
      if (texts.nonEmpty) Some(texts.mkString("\n")) else None
    }

    val items: Seq[Input] = nonSystemMessages.flatMap {
      case UserMessage(content, _) =>
        Seq(Message.InputText(content, ChatRole.User))

      case UserSeqMessage(contents, _) =>
        val inputContents = contents.map {
          case TextContent(t) => InputMessageContent.Text(t)
          case ImageURLContent(url) =>
            InputMessageContent.Image(imageUrl = Some(url))
          case FileContent(fileId, fileData, filename) =>
            InputMessageContent.File(
              fileData = fileData,
              fileId = fileId,
              fileUrl = None,
              filename = filename
            )
        }
        Seq(Message.InputContent(inputContents, ChatRole.User))

      case AssistantMessage(content, _, _) =>
        if (content.nonEmpty) Seq(Message.InputText(content, ChatRole.Assistant))
        else Seq.empty

      case AssistantToolMessage(contentOpt, _, toolCalls) =>
        val contentItems = contentOpt
          .filter(_.nonEmpty)
          .map { content =>
            Message.InputText(content, ChatRole.Assistant)
          }
          .toSeq

        val toolCallItems = toolCalls.collect { case (callId, FunctionCallSpec(name, args)) =>
          FunctionToolCall(
            arguments = args,
            callId = callId,
            name = name
          )
        }

        contentItems ++ toolCallItems

      case ToolMessage(content, toolCallId, _) =>
        Seq(
          FunctionToolCallOutput(
            callId = toolCallId,
            output = FunctionToolOutput.StringOutput(content.getOrElse(""))
          )
        )

      case other =>
        logger.warn(
          s"Responses API adapter: unsupported message type ${other.getClass.getSimpleName}, skipping"
        )
        Seq.empty
    }

    (instructions, items)
  }

  private def toResponsesSettings(
    settings: CreateChatCompletionSettings,
    instructions: Option[String],
    tools: Seq[ResponsesFunctionTool],
    toolChoice: Option[ToolChoice]
  ): CreateModelResponseSettings = {
    if (settings.stop.nonEmpty)
      logger.warn("Responses API adapter: 'stop' parameter is not supported, ignoring")
    if (settings.n.exists(_ > 1))
      logger.warn("Responses API adapter: 'n' > 1 is not supported, ignoring")
    if (settings.frequency_penalty.isDefined)
      logger.warn(
        "Responses API adapter: 'frequency_penalty' parameter is not supported, ignoring"
      )
    if (settings.presence_penalty.isDefined)
      logger.warn(
        "Responses API adapter: 'presence_penalty' parameter is not supported, ignoring"
      )
    if (settings.logit_bias.nonEmpty)
      logger.warn("Responses API adapter: 'logit_bias' parameter is not supported, ignoring")
    if (settings.logprobs.isDefined)
      logger.warn("Responses API adapter: 'logprobs' parameter is not supported, ignoring")
    if (settings.seed.isDefined)
      logger.warn("Responses API adapter: 'seed' parameter is not supported, ignoring")
    if (settings.verbosity.isDefined)
      logger.warn("Responses API adapter: 'verbosity' parameter is not supported, ignoring")
    if (settings.extra_params.nonEmpty)
      logger.warn(
        "Responses API adapter: 'extra_params' parameter is not supported, ignoring"
      )

    val text: Option[TextResponseConfig] = settings.response_format_type.flatMap {
      case ChatCompletionResponseFormatType.text => None

      case ChatCompletionResponseFormatType.json_object =>
        Some(TextResponseConfig(ResponseFormat.JsonObject))

      case ChatCompletionResponseFormatType.json_schema =>
        settings.jsonSchema.map { schemaDef =>
          schemaDef.structure match {
            case Left(schema) =>
              TextResponseConfig(
                ResponseFormat.JsonSchemaSpec(
                  schema = schema,
                  name = Some(schemaDef.name),
                  strict = Some(schemaDef.strict)
                )
              )
            case Right(_) =>
              logger.warn(
                "Responses API adapter: Map-based JSON schema not supported, using json_object format instead"
              )
              TextResponseConfig(ResponseFormat.JsonObject)
          }
        }
    }

    val reasoning: Option[ReasoningConfig] =
      settings.reasoning_effort.map(effort => ReasoningConfig(effort = Some(effort)))

    CreateModelResponseSettings(
      model = settings.model,
      instructions = instructions,
      maxOutputTokens = settings.max_tokens,
      metadata = if (settings.metadata.nonEmpty) Some(settings.metadata) else None,
      parallelToolCalls = settings.parallel_tool_calls,
      reasoning = reasoning,
      store = settings.store,
      temperature = settings.temperature,
      text = text,
      toolChoice = toolChoice,
      tools = tools,
      topP = settings.top_p,
      user = settings.user,
      serviceTier = settings.service_tier.map(_.toString),
      topLogprobs = settings.top_logprobs
    )
  }

  private def toOpenAIChatCompletionResponse(
    response: Response
  ): ChatCompletionResponse = {
    warnUnrepresentableOutputs(response)

    val (textOpt, refusalOpt) = extractTextAndRefusal(response)
    val finishReason = toFinishReason(response, refusalOpt.isDefined && textOpt.isEmpty)

    ChatCompletionResponse(
      id = response.id,
      created = response.createdAt,
      model = response.model,
      system_fingerprint = None,
      choices = Seq(
        ChatCompletionChoiceInfo(
          message = AssistantMessage(
            content = textOpt.getOrElse(""),
            refusal = refusalOpt
          ),
          index = 0,
          finish_reason = finishReason,
          logprobs = None
        )
      ),
      usage = response.usage.map(toUsageInfo),
      originalResponse = Some(response)
    )
  }

  private def toOpenAIToolCompletionResponse(
    response: Response
  ): ChatToolCompletionResponse = {
    warnUnrepresentableOutputs(response)

    val functionCalls = response.outputFunctionCalls
    val toolCalls: Seq[(String, ToolCallSpec)] = functionCalls.map { fc =>
      (fc.callId, FunctionCallSpec(fc.name, fc.arguments))
    }
    val (textOpt, refusalOpt) = extractTextAndRefusal(response)
    val refusalOnly = refusalOpt.isDefined && textOpt.isEmpty && toolCalls.isEmpty
    val finishReason = toFinishReason(response, refusalOnly)

    ChatToolCompletionResponse(
      id = response.id,
      created = response.createdAt,
      model = response.model,
      system_fingerprint = None,
      choices = Seq(
        ChatToolCompletionChoiceInfo(
          message = AssistantToolMessage(
            content = textOpt,
            name = None,
            tool_calls = toolCalls
          ),
          index = 0,
          finish_reason = finishReason
        )
      ),
      usage = response.usage.map(toUsageInfo),
      originalResponse = Some(response)
    )
  }

  private def extractTextAndRefusal(
    response: Response
  ): (Option[String], Option[String]) = {
    val texts = response.outputMessageContents.collect { case e: OutputText => e.text }
    val refusals = response.outputMessageContents.collect { case r: Refusal => r.refusal }
    val textOpt = if (texts.isEmpty) None else Some(texts.mkString("\n"))
    val refusalOpt = if (refusals.isEmpty) None else Some(refusals.mkString("\n"))
    (textOpt, refusalOpt)
  }

  private def warnUnrepresentableOutputs(response: Response): Unit = {
    val unrepresented = response.output.collect {
      case _: Message.OutputContent => None
      case _: FunctionToolCall      => None
      case other                    => Some(other.`type`)
    }.flatten

    if (unrepresented.nonEmpty) {
      logger.warn(
        "Responses API adapter: dropping output items not representable in a chat completion: " +
          unrepresented.distinct.mkString(", ") +
          " (full data preserved in ChatCompletionResponse.originalResponse)"
      )
    }
  }

  private def toFinishReason(
    response: Response,
    refusalOnly: Boolean
  ): Option[String] = {
    if (refusalOnly) Some("content_filter")
    else
      response.status match {
        case ModelStatus.Completed => Some("stop")
        case ModelStatus.Incomplete =>
          response.incompleteDetails.map(_.reason) match {
            case Some("content_filter")    => Some("content_filter")
            case Some("max_output_tokens") => Some("length")
            case _                         => Some("length")
          }
        case ModelStatus.Failed    => Some("error")
        case ModelStatus.Cancelled => Some("stop")
        case _                     => None
      }
  }

  private def toUsageInfo(usage: UsageInfo): ChatUsageInfo =
    ChatUsageInfo(
      prompt_tokens = usage.inputTokens,
      total_tokens = usage.totalTokens,
      completion_tokens = Some(usage.outputTokens),
      prompt_tokens_details = usage.inputTokensDetails.map(d =>
        PromptTokensDetails(
          cached_tokens = d.cachedTokens.getOrElse(0),
          audio_tokens = None
        )
      ),
      completion_tokens_details = usage.outputTokensDetails.map(d =>
        CompletionTokenDetails(
          reasoning_tokens = Some(d.reasoningTokens)
        )
      )
    )

  override def close(): Unit = {
    underlying.close()
  }
}

object OpenAIResponsesChatCompletionService {

  def apply(
    underlying: OpenAIResponsesService with CloseableService
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    new OpenAIResponsesChatCompletionService(underlying)
}
