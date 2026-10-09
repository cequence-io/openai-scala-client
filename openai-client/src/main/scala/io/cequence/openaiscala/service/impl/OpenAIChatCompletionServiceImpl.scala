package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{AssistantTool, BaseMessage, ChatCompletionTool, ModelId}
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.service.adapter.{
  ChatCompletionSettingsConversions,
  MessageConversions
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.ResponseImplicits._
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.Future

/**
 * Private impl. of [[OpenAIChatCompletionService]].
 *
 * @since March
 *   2024
 */
private[service] trait OpenAIChatCompletionServiceImpl
    extends OpenAIChatCompletionService
    with OpenAIServiceWSBase
    with ChatCompletionBodyMaker {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    if (ChatCompletionSettingsConversions.chatRequiresResponsesAPI(settings))
      // a run paused for approval, Responses-native tools, a reasoning mode and the Ultrafast
      // tier live on the Responses API
      responsesBackedChatCompletion match {
        case Some(service) =>
          service.createChatCompletion(messages, settings)

        case None if settings.toolApprovalDecisions.nonEmpty =>
          unsupportedToolApprovalDecisions(settings)

        case None =>
          // refused, never dropped; a chat-only service leaves the tier to the API (another
          // OpenAI-compatible provider may serve it)
          unsupportedResponsesSettings(settings)
            .map(e => Future.failed[ChatCompletionResponse](e))
            .getOrElse(createChatCompletionAux(messages, settings))
      }
    else
      createChatCompletionAux(messages, settings)

  private def createChatCompletionAux(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    execPOST(
      EndPoint.chat_completions,
      bodyParams =
        createBodyParamsForChatCompletion(messages.toList, settings, stream = false).toList
    ).map(
      _.asSafeJson[ChatCompletionResponse]
    )

  // Responses-native tools / a reasoning mode on a chat-only service
  private def unsupportedResponsesSettings(
    settings: CreateChatCompletionSettings
  ): Option[OpenAIScalaClientException] =
    ResponsesChatCompletionSettingsOps.unsupportedResponsesSettings(
      settings,
      "A chat-only OpenAI service (chat completions API)"
    )

  private def unsupportedToolApprovalDecisions[T](
    settings: CreateChatCompletionSettings
  ): Future[T] =
    Future.failed(
      ToolApprovalSettingsOps
        .unsupportedDecisions(settings, "A chat-only OpenAI service (chat completions API)")
        .getOrElse(new OpenAIScalaClientException("No tool approval decisions."))
    )

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * The Responses-backed chat completion of this service, when it also serves the Responses
   * API (the full `OpenAIService` overrides this): completions the chat completions API cannot
   * carry are routed through it - GPT-6 Astra / 6.1 function tools, the provider-neutral
   * MCPServerTool / SkillTool and the Responses-only settings of
   * `ChatCompletionSettingsConversions.chatRequiresResponsesAPI`. `None` (the default) makes
   * such calls fail fast instead, except the Ultrafast tier, which is left to the API.
   */
  protected def responsesBackedChatCompletion: Option[OpenAIChatCompletionService] = None

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse] =
    if (
      tools.nonEmpty && ChatCompletionSettingsConversions.chatToolsUnsupported(settings.model)
    )
      Future.failed(
        new OpenAIScalaClientException(
          ChatCompletionBodyMaker.toolsUnsupportedMessage(settings.model)
        )
      )
    else if (ChatCompletionSettingsConversions.chatToolsPreferResponsesAPI(settings, tools))
      responsesBackedChatCompletion match {
        case Some(service) =>
          logger.debug(
            s"Routing createChatToolCompletion (${settings.model}) through the Responses API - the chat completions API cannot serve it (with reasoning)."
          )
          service.createChatToolCompletion(messages, tools, responseToolChoice, settings)

        case None if settings.toolApprovalDecisions.nonEmpty =>
          unsupportedToolApprovalDecisions(settings)

        case None if unsupportedResponsesSettings(settings).isDefined =>
          Future.failed(unsupportedResponsesSettings(settings).get)

        // chat-only service: GPT-5.6 / GPT-6 Sol/Luna still work there with reasoning 'none'
        case None if !chatToolsRequireResponsesAPI(settings.model, tools) =>
          createChatToolCompletionAux(
            messages,
            tools,
            responseToolChoice,
            settingsForChatToolCompletion(settings)
          )

        case None =>
          Future.failed(
            new OpenAIScalaClientException(
              ChatCompletionBodyMaker.responsesOnlyToolsMessage(settings.model, tools) +
                " Use the full OpenAIService (OpenAIServiceFactory), which routes such tool completions through the Responses API automatically, " +
                "or wrap a Responses-capable service in OpenAIResponsesChatCompletionService."
            )
          )
      }
    else
      createChatToolCompletionAux(
        messages,
        tools,
        responseToolChoice,
        settingsForChatToolCompletion(settings)
      )

  private def createChatToolCompletionAux(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] = {
    val coreParams =
      createBodyParamsForChatCompletion(messages, settings, stream = false)

    execPOST(
      EndPoint.chat_completions,
      bodyParams = coreParams ++ createToolBodyParams(tools, responseToolChoice)
    ).map(
      _.asSafeJson[ChatToolCompletionResponse]
    )
  }

}

trait ChatCompletionBodyMaker {

  // GPT-5.x / GPT-6.x are dispatched on the parsed minor version
  // (ChatCompletionSettingsConversions.gpt5Minor / gpt6Minor)

  // Function tools on the chat completions API - see ChatCompletionSettingsConversions.gpt5_5ChatTools
  // & gpt5_6ChatTools. GPT-6 Astra and GPT-6.1 Sol don't support them at all (Responses API only).
  protected def chatToolsRequireResponsesAPI(model: String): Boolean =
    ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(model)

  protected def chatToolsRequireResponsesAPI(
    model: String,
    tools: Seq[ChatCompletionTool]
  ): Boolean =
    ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(model, tools)

  protected def settingsForChatToolCompletion(
    settings: CreateChatCompletionSettings
  ): CreateChatCompletionSettings = {
    // the bare id, so the rules also apply to Bedrock's `openai.` / `us.openai.` ids
    val model = ChatCompletionSettingsConversions.canonicalOpenAIModel(settings.model)

    val gpt5Minor = ChatCompletionSettingsConversions.gpt5Minor(model)

    // GPT-6 Astra / 6.1 never get here (routed to the Responses API); GPT-6 Sol/Luna follow
    // GPT-5.6
    if (
      gpt5Minor.exists(_ >= 6) || ChatCompletionSettingsConversions.gpt6Minor(model).isDefined
    )
      ChatCompletionSettingsConversions.gpt5_6ChatTools(settings)
    else if (ChatCompletionSettingsConversions.chatToolsRejectExplicitReasoning(model))
      // GPT-5.4 / 5.5: an explicit effort (other than 'none') is rejected with tools
      ChatCompletionSettingsConversions.gpt5_5ChatTools(settings)
    else
      settings
  }

  // `tools` / `tool_choice` body params shared by the sync and streamed tool completions
  protected def createToolBodyParams(
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String]
  ): Seq[(Param, Option[JsValue])] = {
    val toolJsons: Seq[Map[String, Object]] = tools.map {
      case tool: AssistantTool.FunctionTool =>
        Map("type" -> "function", "function" -> Json.toJson(tool))
      case other =>
        // MCPServerTool / SkillTool: Responses API only - never silently dropped
        throw new OpenAIScalaClientException(
          ChatCompletionBodyMaker.responsesOnlyToolsMessage("this", Seq(other))
        )
    }

    JsonUtil.jsonBodyParams(
      Param.tools -> Some(toolJsons),
      Param.tool_choice -> responseToolChoice.map(name =>
        Map(
          "type" -> "function",
          "function" -> Map("name" -> name)
        )
      )
    )
  }

  // asks for the trailing usage-only chunk on streamed chat completions, unless the caller
  // already controls `stream_options` through extra_params
  protected def createStreamOptionsParams(
    settings: CreateChatCompletionSettings
  ): Seq[(Param, Option[JsValue])] =
    if (settings.extra_params.contains("stream_options")) Nil
    else JsonUtil.jsonBodyParams(Param.stream_options -> Some(Map("include_usage" -> true)))
  protected def createBodyParamsForChatCompletion(
    messagesAux: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messagesAux.nonEmpty, "At least one message expected.")
    // never dropped silently: the entry points route or refuse them before this point
    ResponsesChatCompletionSettingsOps
      .unsupportedResponsesSettings(settings, "The chat completions API")
      .foreach(throw _)

    // the retired o1-preview / o1-mini took no system messages
    val messagesFinal =
      if (ChatCompletionSettingsConversions.isO1PreviewOrMini(settings.model))
        MessageConversions.systemToUserMessages(messagesAux)
      else
        messagesAux

    val messageJsons = messagesFinal.map(Json.toJson(_)(messageWrites))

    // the bare id, so the rules also apply to Bedrock's `openai.` / `us.openai.` ids; the
    // request itself still carries settings.model untouched
    val model = ChatCompletionSettingsConversions.canonicalOpenAIModel(settings.model)

    val settingsFinal = {
      import ChatCompletionSettingsConversions._

      if (isO1PreviewOrMini(model)) o1Preview(settings)
      else if (isOSeries(model)) o(settings)
      else if (model == ModelId.chat_latest) chatLatest(settings)
      else if (isGpt6ReasoningAlwaysOn(model)) gpt6(settings) // Astra, 6.1 Sol and newer
      else if (gpt6Minor(model).isDefined) gpt6SolLuna(settings)
      else if (isGpt5SearchApi(model)) gpt5SearchApi(settings)
      else if (isMistralSwitchReasoning(model)) mistralSwitchReasoning(settings)
      else if (isMistralGlm(model)) mistralGlm(settings)
      else
        gpt5Minor(model) match {
          case Some(0)                   => gpt5(settings)
          case Some(1)                   => gpt5_1(settings)
          case Some(2)                   => gpt5_2(settings)
          case Some(3)                   => gpt5_3(settings)
          case Some(4)                   => gpt5_4(settings)
          case Some(5)                   => gpt5_5(settings)
          case Some(minor) if minor >= 6 => gpt5_6(settings) // 5.6 and any newer minor
          case _                         => settings
        }
    }

    JsonUtil.jsonBodyParams(
      Param.messages -> Some(messageJsons),
      Param.model -> Some(settingsFinal.model),
      Param.temperature -> settingsFinal.temperature,
      Param.top_p -> settingsFinal.top_p,
      Param.n -> settingsFinal.n,
      Param.stream -> Some(stream),
      Param.stop -> {
        settingsFinal.stop.size match {
          case 0 => None
          case 1 => Some(settingsFinal.stop.head)
          case _ => Some(settingsFinal.stop)
        }
      },
      Param.max_tokens -> settingsFinal.max_tokens,
      Param.presence_penalty -> settingsFinal.presence_penalty,
      Param.frequency_penalty -> settingsFinal.frequency_penalty,
      Param.logit_bias -> {
        if (settingsFinal.logit_bias.isEmpty) None else Some(settingsFinal.logit_bias)
      },
      Param.user -> settingsFinal.user,
      Param.logprobs -> settingsFinal.logprobs,
      Param.top_logprobs -> settingsFinal.top_logprobs,
      Param.seed -> settingsFinal.seed,
      Param.response_format -> {
        settingsFinal.response_format_type.map {
          (formatType: ChatCompletionResponseFormatType) =>
            if (formatType != ChatCompletionResponseFormatType.json_schema)
              Map("type" -> formatType.toString)
            else
              handleJsonSchema(settingsFinal)
        }
      },
      Param.parallel_tool_calls -> settingsFinal.parallel_tool_calls,
      Param.store -> settingsFinal.store,
      Param.reasoning_effort -> settingsFinal.reasoning_effort.map(_.toString()),
      Param.verbosity -> settingsFinal.verbosity.map(_.toString()),
      Param.service_tier -> settingsFinal.service_tier.map(_.toString()),
      Param.metadata -> (if (settingsFinal.metadata.nonEmpty) Some(settingsFinal.metadata)
                         else None),
      Param.extra_params -> {
        // the adapter-only keys (consumed by the Responses adapter, never an API parameter)
        val extraParams = settingsFinal.extra_params --
          ResponsesChatCompletionSettingsOps.knownParams -- ToolApprovalSettingsOps.knownParams
        if (extraParams.nonEmpty) Some(extraParams) else None
      }
    )
  }

  private def handleJsonSchema(
    settings: CreateChatCompletionSettings
  ): Map[String, Any] =
    settings.jsonSchema.map { case JsonSchemaDef(name, strict, structure) =>
      val schemaMap: Map[String, Any] = structure match {
        case Left(schema) =>
          val json = Json.toJson(schema).as[JsObject]
          JsonUtil.toValueMap(json)

        case Right(schema) => schema
      }

      val adjustedSchema: Map[String, Any] = if (strict) {
        // set "additionalProperties" -> false on "object" types if strict
        def addFlagAux(map: Map[String, Any]): Map[String, Any] = {
          val newMap = map.map { case (key, value) =>
            val unwrappedValue = value match {
              case Some(value) => value
              case other       => other
            }

            val newValue = unwrappedValue match {
              case obj: Map[String, Any] =>
                addFlagAux(obj)

              case other =>
                other
            }
            key -> newValue
          }

          if (Seq("object", Some("object")).contains(map.getOrElse("type", ""))) {
            newMap + ("additionalProperties" -> false)
          } else
            newMap
        }

        addFlagAux(schemaMap)
      } else schemaMap

      Map(
        "type" -> "json_schema",
        "json_schema" -> Map(
          "name" -> name,
          "strict" -> strict,
          "schema" -> adjustedSchema
        )
      )
    }.getOrElse(
      // TODO: is it legal?
      Map("type" -> "json_schema")
    )
}

object ChatCompletionBodyMaker {

  def toolsUnsupportedMessage(model: String): String =
    s"Model '$model' does not support function tools (on the chat completions API nor the Responses API) - use a tool-capable model."

  /** Why a tool list cannot go to the chat completions API. */
  def responsesOnlyToolsMessage(
    model: String,
    tools: Seq[ChatCompletionTool]
  ): String = {
    val neutral = tools.collect {
      case t: ChatCompletionTool.MCPServerTool => s"MCPServerTool(${t.name})"
      case t: ChatCompletionTool.SkillTool     => s"SkillTool(${t.skillId})"
    }
    if (neutral.nonEmpty)
      s"${neutral.mkString(", ")} exist on OpenAI only as Responses API tools (mcp / hosted shell), which the chat completions API doesn't have."
    else
      s"$model model doesn't support function tools on the chat completions API (OpenAI: 'To use function tools, use /v1/responses')."
  }
}
