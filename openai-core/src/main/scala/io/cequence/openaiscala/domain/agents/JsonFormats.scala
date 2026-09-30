package io.cequence.openaiscala.domain.agents

import io.cequence.openaiscala.JsonFormats.{reasoningEffortFormat, serviceTierFormat}
import io.cequence.openaiscala.domain.responsesapi.JsonFormats.multiAgentConfigFormat
import io.cequence.openaiscala.domain.responsesapi.MultiAgentConfig
import io.cequence.openaiscala.domain.settings.ReasoningEffort
import io.cequence.wsclient.JsonUtil
import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.{util => ju}

/**
 * JSON formats of the Agents API (beta): writes for the settings / inputs, lenient reads for
 * the resources, items and events (an unknown tool / item / event / required action is kept
 * raw, never a failure).
 */
object JsonFormats {

  private implicit val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  // drops the null / empty-object fields a write produced
  private def compact(json: JsObject): JsObject =
    JsObject(json.fields.filter {
      case (_, JsNull) => false
      case _           => true
    })

  // ---- reasoning ----

  implicit lazy val agentReasoningFormat: OFormat[AgentReasoning] = {
    val reads: Reads[AgentReasoning] = (
      (__ \ "effort").readNullable[JsValue].map(_.flatMap(_.asOpt[ReasoningEffort])) and
        (__ \ "summary").readNullable[String]
    )(AgentReasoning.apply _)
    val writes: OWrites[AgentReasoning] = (
      (__ \ "effort").writeNullable[ReasoningEffort] and
        (__ \ "summary").writeNullable[String]
    )((x: AgentReasoning) => (x.effort, x.summary))
    OFormat(reads, writes)
  }

  // ---- tools ----

  implicit lazy val mcpTransportFormat: OFormat[McpTransport] = new OFormat[McpTransport] {
    override def writes(o: McpTransport): JsObject = o match {
      case McpTransport.Http(serverUrl, authorization, headers) =>
        compact(
          Json.obj(
            "type" -> "http",
            "server_url" -> serverUrl,
            "authorization" -> authorization,
            "headers" -> (if (headers.nonEmpty) Some(headers) else None)
          )
        )
      case McpTransport.Stdio(command, cwd, args, env, envVars) =>
        compact(
          Json.obj(
            "type" -> "stdio",
            "command" -> command,
            "cwd" -> cwd,
            "args" -> (if (args.nonEmpty) Some(args) else None),
            "env" -> (if (env.nonEmpty) Some(env) else None),
            "env_vars" -> (if (envVars.nonEmpty) Some(envVars) else None)
          )
        )
    }

    override def reads(json: JsValue): JsResult[McpTransport] =
      (json \ "type").asOpt[String] match {
        case Some("stdio") =>
          for {
            command <- (json \ "command").validate[String]
            cwd <- (json \ "cwd").validate[String]
          } yield McpTransport.Stdio(
            command,
            cwd,
            (json \ "args").asOpt[Seq[String]].getOrElse(Nil),
            (json \ "env").asOpt[Map[String, String]].getOrElse(Map()),
            (json \ "env_vars").asOpt[Seq[String]].getOrElse(Nil)
          )
        case _ =>
          (json \ "server_url").validate[String].map { serverUrl =>
            McpTransport.Http(
              serverUrl,
              (json \ "authorization").asOpt[String],
              (json \ "headers").asOpt[Map[String, String]].getOrElse(Map())
            )
          }
      }
  }

  implicit lazy val agentToolFormat: Format[AgentTool] = new Format[AgentTool] {
    override def writes(o: AgentTool): JsValue = o match {
      case AgentTool.Function(name, description, parameters, deferLoading) =>
        compact(
          Json.obj(
            "type" -> "function",
            "name" -> name,
            "description" -> description,
            "parameters" -> parameters,
            "defer_loading" -> deferLoading
          )
        )
      case AgentTool.ToolSearch => Json.obj("type" -> "tool_search")
      case AgentTool.ProgrammaticToolCalling(enabled) =>
        compact(Json.obj("type" -> "programmatic_tool_calling", "enabled" -> enabled))
      case tool: AgentTool.Mcp =>
        compact(
          Json.obj(
            "type" -> "mcp",
            "server_label" -> tool.serverLabel,
            "transport" -> Json.toJson(tool.transport),
            "credential_id" -> tool.credentialId,
            "allowed_tools" -> tool.allowedTools,
            "required" -> tool.required,
            "request_metadata" -> tool.requestMetadata,
            "connection_origin" -> tool.connectionOrigin
          )
        )
      case tool: AgentTool.WebSearch =>
        compact(
          Json.obj(
            "type" -> "web_search",
            "mode" -> tool.mode,
            "context_size" -> tool.contextSize,
            "allowed_domains" -> tool.allowedDomains,
            "location" -> tool.location
          )
        )
      case AgentTool.ComputerUse(includeScreenshots) =>
        compact(
          Json.obj("type" -> "computer_use", "include_screenshots" -> includeScreenshots)
        )
      case AgentTool.Raw(json) => json
    }

    override def reads(json: JsValue): JsResult[AgentTool] = {
      val raw = json.asOpt[JsObject].getOrElse(Json.obj())
      def str(field: String) = (json \ field).asOpt[String]
      val tool = (json \ "type").asOpt[String] match {
        case Some("function") =>
          for {
            name <- str("name")
            parameters <- (json \ "parameters").asOpt[JsObject].orElse(Some(Json.obj()))
          } yield AgentTool.Function(
            name,
            str("description").getOrElse(""),
            parameters,
            (json \ "defer_loading").asOpt[Boolean]
          )
        case Some("tool_search") => Some(AgentTool.ToolSearch)
        case Some("programmatic_tool_calling") =>
          Some(AgentTool.ProgrammaticToolCalling((json \ "enabled").asOpt[Boolean]))
        case Some("mcp") =>
          for {
            serverLabel <- str("server_label")
            transport <- (json \ "transport").asOpt[McpTransport]
          } yield AgentTool.Mcp(
            serverLabel,
            transport,
            str("credential_id"),
            (json \ "allowed_tools").asOpt[Seq[String]],
            (json \ "required").asOpt[Boolean],
            (json \ "request_metadata").asOpt[JsObject],
            (json \ "connection_origin").asOpt[JsObject]
          )
        case Some("web_search") =>
          Some(
            AgentTool.WebSearch(
              str("mode"),
              str("context_size"),
              (json \ "allowed_domains").asOpt[Seq[String]],
              (json \ "location").asOpt[JsObject]
            )
          )
        case Some("computer_use") =>
          Some(AgentTool.ComputerUse((json \ "include_screenshots").asOpt[Boolean]))
        case _ => None
      }
      JsSuccess(tool.getOrElse(AgentTool.Raw(raw)))
    }
  }

  // ---- environment ----

  implicit lazy val agentEnvironmentWrites: OWrites[AgentEnvironment] = {
    case AgentEnvironment.NoEnvironment => Json.obj("type" -> "none")
    case env: AgentEnvironment.OpenAIHosted =>
      compact(
        Json.obj(
          "type" -> "openai_hosted",
          "environment_template_id" -> env.environmentTemplateId,
          "container_size" -> env.containerSize,
          "setup_commands" -> (if (env.setupCommands.nonEmpty) Some(env.setupCommands)
                               else None),
          "env" -> (if (env.env.nonEmpty) Some(env.env) else None),
          "capability_directories" ->
            (if (env.capabilityDirectories.nonEmpty) Some(env.capabilityDirectories) else None)
        )
      ) ++ env.extra
    case env: AgentEnvironment.SelfHosted =>
      compact(
        Json.obj(
          "type" -> "self_hosted",
          "workspace_directory" -> env.workspaceDirectory,
          "capability_directories" ->
            (if (env.capabilityDirectories.nonEmpty) Some(env.capabilityDirectories) else None)
        )
      )
  }

  // ---- agent configuration / settings ----

  implicit lazy val agentConfigWrites: OWrites[AgentConfig] = (config: AgentConfig) =>
    compact(
      Json.obj(
        "model" -> config.model,
        "instructions" -> config.instructions,
        "reasoning" -> config.reasoning,
        "text" -> config.text,
        "service_tier" -> config.serviceTier,
        "tools" -> config.tools,
        "multi_agent" -> config.multiAgent
      )
    )

  implicit lazy val createAgentSettingsWrites: OWrites[CreateAgentSettings] =
    (settings: CreateAgentSettings) =>
      compact(
        Json.obj(
          "model" -> settings.model,
          "name" -> settings.name,
          "instructions" -> settings.instructions,
          "reasoning" -> settings.reasoning,
          "text" -> settings.text,
          "service_tier" -> settings.serviceTier,
          "tools" -> (if (settings.tools.nonEmpty) Some(settings.tools) else None),
          "multi_agent" -> settings.multiAgent,
          "metadata" -> (if (settings.metadata.nonEmpty) Some(settings.metadata) else None)
        )
      )

  implicit lazy val updateAgentSettingsWrites: OWrites[UpdateAgentSettings] =
    (settings: UpdateAgentSettings) =>
      compact(
        Json.obj(
          "model" -> settings.model,
          "name" -> settings.name,
          "instructions" -> settings.instructions,
          "reasoning" -> settings.reasoning,
          "text" -> settings.text,
          "service_tier" -> settings.serviceTier,
          "tools" -> settings.tools,
          "multi_agent" -> settings.multiAgent,
          "metadata" -> settings.metadata
        )
      )

  // ---- inputs ----

  implicit lazy val agentInputContentFormat: Format[AgentInputContent] =
    new Format[AgentInputContent] {
      override def writes(o: AgentInputContent): JsValue = o match {
        case AgentInputContent.Text(text) => Json.obj("type" -> "input_text", "text" -> text)
        case AgentInputContent.Image(imageUrl) =>
          Json.obj("type" -> "input_image", "image_url" -> imageUrl)
      }

      override def reads(json: JsValue): JsResult[AgentInputContent] =
        (json \ "type").asOpt[String] match {
          case Some("input_image") =>
            (json \ "image_url").validate[String].map(AgentInputContent.Image)
          case _ => (json \ "text").validate[String].map(AgentInputContent.Text)
        }
    }

  implicit lazy val agentUserMessageWrites: OWrites[AgentUserMessage] =
    (message: AgentUserMessage) =>
      Json.obj("type" -> "message", "role" -> "user", "content" -> message.content)

  implicit lazy val agentInputWrites: Writes[AgentInput] = {
    case AgentInput.Text(text)         => JsString(text)
    case AgentInput.Messages(messages) => Json.toJson(messages)
  }

  implicit lazy val agentSessionInputWrites: OWrites[AgentSessionInput] = {
    case AgentSessionInput.Message(messages) =>
      Json.obj("type" -> "agent.session.input.message", "input" -> messages)
    case AgentSessionInput.Cancel =>
      Json.obj("type" -> "agent.session.input.cancel")
    case result: AgentSessionInput.ToolResult =>
      val output: Option[JsValue] =
        if (result.outputContent.nonEmpty) Some(Json.toJson(result.outputContent))
        else result.output.map(JsString)
      compact(
        Json.obj(
          "type" -> "agent.session.input.tool_result",
          "turn_id" -> result.turnId,
          "call_id" -> result.callId,
          "success" -> result.success,
          "output" -> output,
          "error" -> result.error
        )
      )
    case AgentSessionInput.ComputerUseApprovalResult(requestId, response) =>
      Json.obj(
        "type" -> "agent.session.input.computer_use_approval_request_result",
        "request_id" -> requestId,
        "response" -> response
      )
  }

  /** The body of `POST /agents/sessions`. */
  def createAgentSessionBody(
    settings: CreateAgentSessionSettings,
    input: Option[AgentInput],
    stream: Boolean
  ): JsObject =
    compact(
      Json.obj(
        "agent" -> settings.agent,
        "agent_id" -> settings.agentId,
        "environment" -> Json.toJson(settings.environment),
        "vault_ids" -> (if (settings.vaultIds.nonEmpty) Some(settings.vaultIds) else None),
        "metadata" -> (if (settings.metadata.nonEmpty) Some(settings.metadata) else None),
        "input" -> input,
        "stream" -> (if (stream) Some(true) else None)
      )
    )

  // ---- resources ----

  // an object only - a null usage (the API sends it often) is absent, not all zeros
  implicit lazy val agentTokenUsageReads: Reads[AgentTokenUsage] = {
    val fields: Reads[AgentTokenUsage] = (
      (__ \ "input_tokens").readWithDefault[Int](0) and
        (__ \ "output_tokens").readWithDefault[Int](0) and
        (__ \ "total_tokens").readWithDefault[Int](0) and
        (__ \ "input_tokens_details").readNullable[JsObject] and
        (__ \ "output_tokens_details").readNullable[JsObject]
    )(AgentTokenUsage.apply _)
    Reads {
      case obj: JsObject => fields.reads(obj)
      case other         => JsError(s"Expected a usage object, got: $other")
    }
  }

  // lenient: an absent / null / malformed optional sub-object reads as None
  private def lenient[T: Reads](path: JsPath): Reads[Option[T]] =
    path.readNullable[JsValue].map(_.flatMap(_.asOpt[T]))

  implicit lazy val agentReads: Reads[Agent] = (
    (__ \ "id").read[String] and
      (__ \ "model").read[String] and
      (__ \ "name").readNullable[String] and
      (__ \ "instructions").readNullable[String] and
      lenient[AgentReasoning](__ \ "reasoning") and
      (__ \ "text").readNullable[JsObject] and
      (__ \ "service_tier").readNullable[String] and
      (__ \ "tools").readWithDefault[Seq[AgentTool]](Nil) and
      lenient[MultiAgentConfig](__ \ "multi_agent") and
      (__ \ "metadata").readWithDefault[Map[String, String]](Map()) and
      (__ \ "created_at").readNullable[ju.Date] and
      (__ \ "updated_at").readNullable[ju.Date]
  )(Agent.apply _)

  implicit lazy val agentRequiredActionReads: Reads[AgentRequiredAction] = Reads { json =>
    val raw = json.asOpt[JsObject].getOrElse(Json.obj())
    def str(field: String) = (json \ field).asOpt[String]
    val action = (json \ "type").asOpt[String] match {
      case Some("function_call") =>
        for {
          turnId <- str("turn_id")
          callId <- str("call_id")
          name <- str("name")
        } yield AgentRequiredAction.FunctionCall(
          turnId,
          callId,
          name,
          (json \ "arguments").toOption.getOrElse(Json.obj())
        )
      case Some("computer_use_approval_request") =>
        for {
          turnId <- str("turn_id")
          requestId <- str("request_id")
        } yield AgentRequiredAction.ComputerUseApprovalRequest(
          turnId,
          requestId,
          (json \ "request").toOption.getOrElse(JsNull)
        )
      case Some("environment_connection") =>
        str("environment_id").map(AgentRequiredAction.EnvironmentConnection)
      case _ => None
    }
    JsSuccess(
      action.getOrElse(
        AgentRequiredAction.Other((json \ "type").asOpt[String].getOrElse(""), raw)
      )
    )
  }

  implicit lazy val agentSessionReads: Reads[AgentSession] = (
    (__ \ "id").read[String] and
      (__ \ "status").read[String] and
      (__ \ "created_at").read[ju.Date] and
      (__ \ "last_active_at").readNullable[ju.Date] and
      (__ \ "required_actions").readWithDefault[Seq[AgentRequiredAction]](Nil) and
      lenient[String](__ \ "error") and
      lenient[Agent](__ \ "agent") and
      (__ \ "environment").readNullable[JsObject] and
      (__ \ "vault_ids").readWithDefault[Seq[String]](Nil) and
      lenient[AgentTokenUsage](__ \ "usage") and
      (__ \ "metadata").readWithDefault[Map[String, String]](Map())
  )(AgentSession.apply _)

  implicit lazy val agentTurnErrorReads: Reads[AgentTurnError] = (
    (__ \ "code").readWithDefault[String]("") and
      (__ \ "message").readWithDefault[String]("")
  )(AgentTurnError.apply _)

  implicit lazy val agentTurnReads: Reads[AgentTurn] = (
    (__ \ "id").read[String] and
      (__ \ "session_id").read[String] and
      (__ \ "status").read[String] and
      (__ \ "agent_id").readNullable[String] and
      (__ \ "subagent_id").readNullable[String] and
      (__ \ "created_at").readNullable[ju.Date] and
      (__ \ "started_at").readNullable[ju.Date] and
      (__ \ "completed_at").readNullable[ju.Date] and
      lenient[AgentTurnError](__ \ "error") and
      lenient[AgentTokenUsage](__ \ "usage")
  )(AgentTurn.apply _)

  implicit lazy val agentSubagentReads: Reads[AgentSubagent] = (
    (__ \ "id").read[String] and
      (__ \ "session_id").read[String] and
      (__ \ "status").readWithDefault[String]("") and
      (__ \ "parent_agent_id").readNullable[String] and
      (__ \ "name").readNullable[String] and
      (__ \ "instructions").readNullable[JsValue] and
      (__ \ "opened_at").readNullable[ju.Date] and
      (__ \ "closed_at").readNullable[ju.Date]
  )(AgentSubagent.apply _)

  implicit lazy val agentSessionArtifactReads: Reads[AgentSessionArtifact] = (
    (__ \ "id").read[String] and
      (__ \ "session_id").read[String] and
      (__ \ "path").read[String] and
      (__ \ "size_bytes").readWithDefault[Long](0L) and
      (__ \ "environment_id").readNullable[String] and
      (__ \ "turn_id").readNullable[String] and
      (__ \ "created_at").readNullable[ju.Date]
  )(AgentSessionArtifact.apply _)

  implicit lazy val agentEnvironmentTemplateReads: Reads[AgentEnvironmentTemplate] =
    Reads { json =>
      for {
        raw <- json.validate[JsObject]
        id <- (json \ "id").validate[String]
      } yield AgentEnvironmentTemplate(id, (json \ "name").asOpt[String], raw)
    }

  implicit def agentsPageReads[T: Reads]: Reads[AgentsPage[T]] = (
    (__ \ "data").readWithDefault[Seq[T]](Nil) and
      (__ \ "first_id").readNullable[String] and
      (__ \ "last_id").readNullable[String] and
      (__ \ "has_more").readWithDefault[Boolean](false)
  )(AgentsPage.apply[T] _)

  implicit lazy val agentsDeletedReads: Reads[AgentsDeleted] = (
    (__ \ "id").read[String] and
      (__ \ "deleted").readWithDefault[Boolean](false)
  )(AgentsDeleted.apply _)

  // ---- items ----

  implicit lazy val agentMessageContentReads: Reads[AgentMessageContent] = Reads { json =>
    val raw = json.asOpt[JsObject].getOrElse(Json.obj())
    JsSuccess((json \ "type").asOpt[String] match {
      case Some("output_text") =>
        AgentMessageContent.OutputText((json \ "text").asOpt[String].getOrElse(""))
      case Some("input_text") =>
        AgentMessageContent.InputText((json \ "text").asOpt[String].getOrElse(""))
      case Some("input_image") =>
        (json \ "image_url")
          .asOpt[String]
          .map(AgentMessageContent.InputImage)
          .getOrElse(AgentMessageContent.Other(raw))
      case _ => AgentMessageContent.Other(raw)
    })
  }

  implicit lazy val agentSessionItemReads: Reads[AgentSessionItem] = Reads { json =>
    json.validate[JsObject].map { raw =>
      def str(field: String) = (json \ field).asOpt[String]
      def value(field: String) = (json \ field).toOption.filterNot(_ == JsNull)
      val id = str("id")
      val turnId = str("turn_id")
      val itemType = str("type").getOrElse("")
      val item: Option[AgentSessionItem] = itemType match {
        case "message" =>
          Some(
            AgentSessionItem.Message(
              id,
              turnId,
              str("role").getOrElse("assistant"),
              (json \ "content").asOpt[Seq[AgentMessageContent]].getOrElse(Nil),
              str("status"),
              str("phase")
            )
          )
        case "reasoning" =>
          Some(AgentSessionItem.Reasoning(id, turnId, value("summary"), str("status")))
        case "function_call" =>
          for {
            callId <- str("call_id")
            name <- str("name")
          } yield AgentSessionItem.FunctionCall(
            id,
            turnId,
            callId,
            name,
            value("arguments").getOrElse(Json.obj()),
            str("status")
          )
        case "function_call_output" =>
          str("call_id").map { callId =>
            AgentSessionItem.FunctionCallOutput(
              id,
              turnId,
              callId,
              value("output"),
              str("error"),
              str("status")
            )
          }
        case "mcp_call" =>
          Some(
            AgentSessionItem.McpCall(
              id,
              turnId,
              str("server_label").getOrElse(""),
              str("name").getOrElse(""),
              value("arguments"),
              value("output"),
              value("error"),
              str("status")
            )
          )
        case "command_execution" =>
          Some(
            AgentSessionItem.CommandExecution(
              id,
              turnId,
              str("command").getOrElse(""),
              str("cwd"),
              str("output"),
              (json \ "exit_code").asOpt[Int],
              (json \ "duration_ms").asOpt[Long],
              str("status")
            )
          )
        case "web_search_call" =>
          Some(AgentSessionItem.WebSearchCall(id, turnId, value("action"), str("status")))
        case _ => None
      }
      item.getOrElse(AgentSessionItem.Other(itemType, raw))
    }
  }

  // ---- events ----

  implicit lazy val agentSessionEventReads: Reads[AgentSessionEvent] = Reads { json =>
    json.validate[JsObject].map { raw =>
      import AgentSessionEvent._
      def str(field: String) = (json \ field).asOpt[String]
      def int(field: String) = (json \ field).asOpt[Int].getOrElse(0)
      val eventType = str("type").getOrElse("")
      val eventId = str("event_id")
      val sessionId = str("session_id").getOrElse("")
      val turnId = str("turn_id").getOrElse("")

      val event: Option[AgentSessionEvent] = eventType match {
        case SessionCreated | SessionInProgress | SessionIdle |
            SessionRequiresAction | SessionFailed =>
          (json \ "session").asOpt[AgentSession].map(SessionUpdated(eventType, eventId, _))

        case TurnCreated | TurnInProgress | TurnCompleted | TurnFailed | TurnCancelled =>
          (json \ "turn").asOpt[AgentTurn].map { turn =>
            TurnUpdated(
              eventType,
              eventId,
              str("session_id").getOrElse(turn.sessionId),
              str("turn_id").getOrElse(turn.id),
              turn,
              (json \ "usage").asOpt[AgentTokenUsage]
            )
          }

        case "agent.session.turn.item.added" =>
          (json \ "item")
            .asOpt[AgentSessionItem]
            .map(ItemAdded(eventId, sessionId, turnId, int("output_index"), _))

        case "agent.session.turn.item.done" =>
          (json \ "item")
            .asOpt[AgentSessionItem]
            .map(ItemDone(eventId, sessionId, turnId, int("output_index"), _))

        case "agent.session.turn.output_text.delta" =>
          for {
            itemId <- str("item_id")
            delta <- str("delta")
          } yield OutputTextDelta(
            eventId,
            sessionId,
            turnId,
            itemId,
            int("output_index"),
            int("content_index"),
            delta
          )

        case "agent.session.turn.output_text.done" =>
          for {
            itemId <- str("item_id")
            text <- str("text")
          } yield OutputTextDone(
            eventId,
            sessionId,
            turnId,
            itemId,
            int("output_index"),
            int("content_index"),
            text
          )

        case "agent.session.turn.reasoning_summary_text.delta" =>
          for {
            itemId <- str("item_id")
            delta <- str("delta")
          } yield ReasoningSummaryTextDelta(
            eventId,
            sessionId,
            turnId,
            itemId,
            int("output_index"),
            int("summary_index"),
            delta
          )

        case "agent.session.turn.reasoning_summary_text.done" =>
          for {
            itemId <- str("item_id")
            text <- str("text")
          } yield ReasoningSummaryTextDone(
            eventId,
            sessionId,
            turnId,
            itemId,
            int("output_index"),
            int("summary_index"),
            text
          )

        case "agent.output.command_execution_output.delta" =>
          for {
            itemId <- str("item_id")
            delta <- str("delta")
          } yield CommandExecutionOutputDelta(
            eventId,
            sessionId,
            turnId,
            itemId,
            int("output_index"),
            delta
          )

        case "error" =>
          Some(
            AgentSessionEvent.Error(
              eventId,
              str("session_id"),
              (json \ "error").toOption.getOrElse(raw)
            )
          )

        case _ => None
      }

      event.getOrElse(Other(eventType, raw))
    }
  }
}
