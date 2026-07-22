package io.cequence.openaiscala.claudeagent

import io.cequence.openaiscala.claudeagent.domain.{ClaudeAgentEvent, PermissionDecision}
import play.api.libs.json._

object JsonFormats {

  /**
   * Parses one already-decoded top-level JSON object from the CLI's stdout (one NDJSON line)
   * into a [[ClaudeAgentEvent]]. Dispatches on the top-level `type` field (and `subtype` for
   * `system`/`result`). Returns `ClaudeAgentEvent.Unknown` for any shape it doesn't recognize
   * or can't fully parse (never throws on a structurally-valid JSON object with an unexpected
   * shape - only a JsObject is a hard precondition, checked by the caller).
   */
  def parseEvent(json: JsObject): ClaudeAgentEvent = {
    val typeOpt = (json \ "type").asOpt[String]
    val subtypeOpt = (json \ "subtype").asOpt[String]

    typeOpt match {
      case Some("system") =>
        subtypeOpt match {
          case Some("init") => parseSystemInit(json, subtypeOpt)
          case _            => ClaudeAgentEvent.Unknown("system", subtypeOpt, json)
        }

      case Some("assistant") =>
        ClaudeAgentEvent.Assistant(
          message = (json \ "message").asOpt[JsObject].getOrElse(Json.obj()),
          parentToolUseId = (json \ "parent_tool_use_id").asOpt[String],
          sessionId = (json \ "session_id").asOpt[String].getOrElse(""),
          uuid = (json \ "uuid").asOpt[String].getOrElse(""),
          raw = json
        )

      case Some("user") =>
        ClaudeAgentEvent.UserEcho(
          sessionId = (json \ "session_id").asOpt[String],
          isReplay = (json \ "isReplay").asOpt[Boolean].getOrElse(false),
          raw = json
        )

      case Some("result") =>
        subtypeOpt match {
          case Some("success") =>
            ClaudeAgentEvent.ResultSuccess(
              result = (json \ "result").asOpt[String].getOrElse(""),
              totalCostUsd = (json \ "total_cost_usd").asOpt[Double].getOrElse(0.0),
              numTurns = (json \ "num_turns").asOpt[Int].getOrElse(0),
              durationMs = (json \ "duration_ms").asOpt[Long].getOrElse(0L),
              isError = (json \ "is_error").asOpt[Boolean].getOrElse(false),
              sessionId = (json \ "session_id").asOpt[String].getOrElse(""),
              raw = json
            )

          case Some(subtype) if subtype.startsWith("error") =>
            ClaudeAgentEvent.ResultError(
              subtype = subtype,
              errors = (json \ "errors").asOpt[Seq[String]].getOrElse(Nil),
              totalCostUsd = (json \ "total_cost_usd").asOpt[Double].getOrElse(0.0),
              sessionId = (json \ "session_id").asOpt[String].getOrElse(""),
              raw = json
            )

          case _ =>
            ClaudeAgentEvent.Unknown("result", subtypeOpt, json)
        }

      case Some("stream_event") =>
        ClaudeAgentEvent.StreamDelta(
          sessionId = (json \ "session_id").asOpt[String].getOrElse(""),
          raw = json
        )

      case Some("control_request")
          if (json \ "request" \ "subtype").asOpt[String].contains("can_use_tool") =>
        ClaudeAgentEvent.ToolPermissionRequest(
          requestId = (json \ "request_id").asOpt[String].getOrElse(""),
          toolName = (json \ "request" \ "tool_name").asOpt[String].getOrElse(""),
          input = (json \ "request" \ "input").asOpt[JsObject].getOrElse(Json.obj()),
          toolUseId = (json \ "request" \ "tool_use_id").asOpt[String].getOrElse(""),
          raw = json
        )

      case other =>
        val effectiveSubtypeOpt = other match {
          case Some("control_request")  => (json \ "request" \ "subtype").asOpt[String]
          case Some("control_response") => (json \ "response" \ "subtype").asOpt[String]
          case _                        => subtypeOpt
        }
        ClaudeAgentEvent.Unknown(other.getOrElse(""), effectiveSubtypeOpt, json)
    }
  }

  // `system`/`init` requires every field to be present and correctly typed - falls back to
  // Unknown (rather than defaulting individual fields) since a partially-populated handshake
  // signal is more likely a CLI-version mismatch than an event worth modeling precisely.
  private def parseSystemInit(
    json: JsObject,
    subtypeOpt: Option[String]
  ): ClaudeAgentEvent = {
    val parsed = for {
      apiKeySource <- (json \ "apiKeySource").asOpt[String]
      model <- (json \ "model").asOpt[String]
      tools <- (json \ "tools").asOpt[Seq[String]]
      permissionMode <- (json \ "permissionMode").asOpt[String]
      sessionId <- (json \ "session_id").asOpt[String]
      cwd <- (json \ "cwd").asOpt[String]
      claudeCodeVersion <- (json \ "claude_code_version").asOpt[String]
    } yield ClaudeAgentEvent.SystemInit(
      apiKeySource = apiKeySource,
      model = model,
      tools = tools,
      permissionMode = permissionMode,
      sessionId = sessionId,
      cwd = cwd,
      claudeCodeVersion = claudeCodeVersion,
      raw = json
    )

    parsed.getOrElse(ClaudeAgentEvent.Unknown("system", subtypeOpt, json))
  }

  /** Builds the stdin JSON object for a plain user-turn conversational message. */
  def userMessageJson(
    text: String,
    sessionId: String = ""
  ): JsObject =
    Json.obj(
      "type" -> "user",
      "session_id" -> sessionId,
      "message" -> Json.obj(
        "role" -> "user",
        "content" -> Json.arr(
          Json.obj("type" -> "text", "text" -> text)
        )
      ),
      "parent_tool_use_id" -> JsNull
    )

  /**
   * Builds the stdin JSON object for a tool-result reply (folded into a user-turn message per
   * the wire protocol - there is no separate tool_result wire type).
   */
  def toolResultMessageJson(
    toolUseId: String,
    content: String,
    isError: Boolean = false,
    sessionId: String = ""
  ): JsObject = {
    val baseBlock = Json.obj(
      "type" -> "tool_result",
      "tool_use_id" -> toolUseId,
      "content" -> content
    )
    val block = if (isError) baseBlock + ("is_error" -> JsBoolean(true)) else baseBlock

    Json.obj(
      "type" -> "user",
      "session_id" -> sessionId,
      "message" -> Json.obj(
        "role" -> "user",
        "content" -> Json.arr(block)
      ),
      "parent_tool_use_id" -> JsNull
    )
  }

  /**
   * Builds a `control_request` envelope: `{"type":"control_request","request_id":requestId,
   * "request":{"subtype":subtype, ...payload fields spread in...}}`.
   */
  def controlRequestJson(
    requestId: String,
    subtype: String,
    payload: JsObject = Json.obj()
  ): JsObject =
    Json.obj(
      "type" -> "control_request",
      "request_id" -> requestId,
      "request" -> (Json.obj("subtype" -> subtype) ++ payload)
    )

  /**
   * Builds the `response` payload (a `PermissionResult`) for a `control_response` answering a
   * `can_use_tool` control_request: `{"behavior":"allow","updated_input":...}` or
   * `{"behavior":"deny","message":...,"interrupt":...}`.
   */
  def permissionResultJson(decision: PermissionDecision): JsObject =
    decision match {
      case PermissionDecision.Allow(updatedInput) =>
        Json.obj(
          "behavior" -> "allow",
          "updated_input" -> updatedInput
        )

      case PermissionDecision.Deny(message, interrupt) =>
        Json.obj(
          "behavior" -> "deny",
          "message" -> message,
          "interrupt" -> interrupt
        )
    }

  /**
   * Builds a full `control_response` envelope: `{"type":"control_response","response":
   * {"subtype":"success","request_id":requestId,"response":response}}` (or
   * `"subtype":"error","error":...` when `error` is set instead of `response`).
   */
  def controlResponseJson(
    requestId: String,
    response: Option[JsObject] = None,
    error: Option[String] = None
  ): JsObject = {
    val inner = error match {
      case Some(err) =>
        Json.obj(
          "subtype" -> "error",
          "request_id" -> requestId,
          "error" -> err
        )

      case None =>
        val responseValue: JsObject = response.getOrElse(Json.obj())
        Json.obj(
          "subtype" -> "success",
          "request_id" -> requestId,
          "response" -> responseValue
        )
    }

    Json.obj(
      "type" -> "control_response",
      "response" -> inner
    )
  }
}
