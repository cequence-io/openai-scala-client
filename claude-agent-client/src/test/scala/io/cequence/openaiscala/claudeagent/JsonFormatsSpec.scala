package io.cequence.openaiscala.claudeagent

import io.cequence.openaiscala.claudeagent.domain.{ClaudeAgentEvent, PermissionDecision}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

/**
 * Hermetic unit tests for the `claude` CLI stream-json wire codec: [[JsonFormats.parseEvent]]
 * (NDJSON line -> [[ClaudeAgentEvent]]) and the stdin/control-protocol builders
 * (`userMessageJson`, `toolResultMessageJson`, `controlRequestJson`, `permissionResultJson`,
 * `controlResponseJson`). No process spawning, no network - pure JSON in, JSON/ADT out.
 */
class JsonFormatsSpec extends AnyWordSpec with Matchers {

  private def parse(raw: String): JsObject = Json.parse(raw).as[JsObject]

  "JsonFormats.parseEvent" should {

    "parse a system/init event into SystemInit" in {
      val raw = parse(
        """{"type":"system","subtype":"init","apiKeySource":"none","model":"claude-haiku-4-5",
          |"tools":["bash","read","write"],"permissionMode":"default",
          |"session_id":"f2fdfebe-1bee-4b10-b495-a0285ed2e559","cwd":"/home/user/project",
          |"claude_code_version":"2.1.215","uuid":"abc-123"}""".stripMargin
      )

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.SystemInit(
        apiKeySource = "none",
        model = "claude-haiku-4-5",
        tools = Seq("bash", "read", "write"),
        permissionMode = "default",
        sessionId = "f2fdfebe-1bee-4b10-b495-a0285ed2e559",
        cwd = "/home/user/project",
        claudeCodeVersion = "2.1.215",
        raw = raw
      )
    }

    "fall back to Unknown for a system/init event missing a required field" in {
      // "model" dropped - parseSystemInit's for-comprehension fails and falls back to Unknown
      // rather than defaulting the missing field.
      val raw = parse(
        """{"type":"system","subtype":"init","apiKeySource":"none",
          |"tools":["bash","read","write"],"permissionMode":"default",
          |"session_id":"f2fdfebe-1bee-4b10-b495-a0285ed2e559","cwd":"/home/user/project",
          |"claude_code_version":"2.1.215","uuid":"abc-123"}""".stripMargin
      )

      JsonFormats
        .parseEvent(raw) shouldBe ClaudeAgentEvent.Unknown("system", Some("init"), raw)
    }

    "parse an assistant event into Assistant with no parent tool use id" in {
      val raw = parse(
        """{"type":"assistant","message":{"id":"msg_01","role":"assistant",
          |"content":[{"type":"text","text":"Hello!"}],"model":"claude-haiku-4-5",
          |"stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}},
          |"parent_tool_use_id":null,"uuid":"u1","session_id":"s1"}""".stripMargin
      )

      val event = JsonFormats.parseEvent(raw)

      event shouldBe a[ClaudeAgentEvent.Assistant]
      val assistantEvent = event.asInstanceOf[ClaudeAgentEvent.Assistant]

      assistantEvent.parentToolUseId shouldBe None
      assistantEvent.sessionId shouldBe "s1"
      assistantEvent.uuid shouldBe "u1"
      assistantEvent.raw shouldBe raw
      (assistantEvent.message \ "content" \ 0 \ "text").as[String] shouldBe "Hello!"
    }

    "parse an assistant event's parent_tool_use_id when present (subagent turn)" in {
      val raw = parse(
        """{"type":"assistant","message":{"id":"msg_02","role":"assistant","content":[]},
          |"parent_tool_use_id":"toolu_xyz","uuid":"u2","session_id":"s1"}""".stripMargin
      )

      val event = JsonFormats.parseEvent(raw).asInstanceOf[ClaudeAgentEvent.Assistant]

      event.parentToolUseId shouldBe Some("toolu_xyz")
    }

    "parse a user echo event into UserEcho" in {
      val raw = parse(
        """{"type":"user","message":{"role":"user","content":"hi"},
          |"parent_tool_use_id":null,"session_id":"s1","isReplay":true}""".stripMargin
      )

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.UserEcho(Some("s1"), true, raw)
    }

    "default isReplay to false when absent from a user echo event" in {
      val raw = parse(
        """{"type":"user","message":{"role":"user","content":"hi"},
          |"parent_tool_use_id":null,"session_id":"s1"}""".stripMargin
      )

      val event = JsonFormats.parseEvent(raw).asInstanceOf[ClaudeAgentEvent.UserEcho]

      event.isReplay shouldBe false
    }

    "parse a result/success event into ResultSuccess" in {
      val raw = parse(
        """{"type":"result","subtype":"success","duration_ms":2133,"duration_api_ms":3311,
          |"is_error":false,"num_turns":1,"result":"OK","stop_reason":"end_turn",
          |"total_cost_usd":0.0297648,"usage":{"input_tokens":10,"output_tokens":61},
          |"session_id":"s1","uuid":"r1"}""".stripMargin
      )

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.ResultSuccess(
        result = "OK",
        totalCostUsd = 0.0297648,
        numTurns = 1,
        durationMs = 2133L,
        isError = false,
        sessionId = "s1",
        raw = raw
      )
    }

    "parse a result/error_* event into ResultError" in {
      val raw = parse(
        """{"type":"result","subtype":"error_max_turns","duration_ms":100,"is_error":true,
          |"num_turns":5,"total_cost_usd":0.5,"errors":["exceeded max turns"],
          |"session_id":"s1"}""".stripMargin
      )

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.ResultError(
        subtype = "error_max_turns",
        errors = Seq("exceeded max turns"),
        totalCostUsd = 0.5,
        sessionId = "s1",
        raw = raw
      )
    }

    "parse a stream_event into StreamDelta" in {
      val raw = parse(
        """{"type":"stream_event","event":{"type":"content_block_delta","index":0,
          |"delta":{"type":"text_delta","text":"Hel"}},"parent_tool_use_id":null,
          |"session_id":"s1","uuid":"e1"}""".stripMargin
      )

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.StreamDelta("s1", raw)
    }

    "parse a CLI-initiated can_use_tool control_request into ToolPermissionRequest" in {
      val raw = parse(
        """{"type":"control_request","request_id":"req_42",
          |"request":{"subtype":"can_use_tool","tool_name":"bash",
          |"input":{"command":"ls -la"},"tool_use_id":"toolu_99"}}""".stripMargin
      )

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.ToolPermissionRequest(
        requestId = "req_42",
        toolName = "bash",
        input = Json.obj("command" -> "ls -la"),
        toolUseId = "toolu_99",
        raw = raw
      )
    }

    "fall back to Unknown for a control_request with a non-can_use_tool subtype" in {
      // Only a nested request.subtype == "can_use_tool" maps to ToolPermissionRequest. The
      // fallback branch reads the nested "request.subtype" (not a top-level "subtype", which
      // control_request envelopes don't carry), so the resulting Unknown still preserves
      // "set_permission_mode" for observability.
      val raw = parse(
        """{"type":"control_request","request_id":"req_43",
          |"request":{"subtype":"set_permission_mode","mode":"acceptEdits"}}""".stripMargin
      )

      JsonFormats.parseEvent(raw) shouldBe
        ClaudeAgentEvent.Unknown("control_request", Some("set_permission_mode"), raw)
    }

    "fall back to Unknown(type, None, ...) for a keep_alive event" in {
      val raw = parse("""{"type":"keep_alive"}""")

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.Unknown("keep_alive", None, raw)
    }

    "treat a JSON null subtype as None rather than throwing" in {
      val raw = parse("""{"type":"session_state_changed","subtype":null,"state":"idle"}""")

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.Unknown(
        "session_state_changed",
        None,
        raw
      )
    }

    "fall back to Unknown(\"\", None, ...) when the type field is absent entirely" in {
      val raw = parse("""{"foo":"bar"}""")

      JsonFormats.parseEvent(raw) shouldBe ClaudeAgentEvent.Unknown("", None, raw)
    }
  }

  "JsonFormats.userMessageJson" should {

    "build the exact user-turn wire envelope" in {
      val expected = parse(
        """{"type":"user","session_id":"sid-1","message":{"role":"user",
          |"content":[{"type":"text","text":"hello"}]},"parent_tool_use_id":null}""".stripMargin
      )

      JsonFormats.userMessageJson("hello", "sid-1") shouldBe expected
    }

    "default session_id to the empty string when omitted" in {
      val result = JsonFormats.userMessageJson("hi")

      (result \ "session_id").as[String] shouldBe ""
    }
  }

  "JsonFormats.toolResultMessageJson" should {

    "omit the is_error key entirely when isError is false" in {
      val result = JsonFormats.toolResultMessageJson("toolu_1", "42", isError = false, "sid-1")
      val block = (result \ "message" \ "content" \ 0).as[JsObject]

      (block \ "type").as[String] shouldBe "tool_result"
      (block \ "tool_use_id").as[String] shouldBe "toolu_1"
      (block \ "content").as[String] shouldBe "42"
      (block \ "is_error").toOption shouldBe None
    }

    "set is_error to true when isError is true" in {
      val result = JsonFormats.toolResultMessageJson("toolu_1", "boom", isError = true)
      val block = (result \ "message" \ "content" \ 0).as[JsObject]

      (block \ "is_error").as[Boolean] shouldBe true
    }
  }

  "JsonFormats.controlRequestJson" should {

    "build a plain subtype-only control_request envelope" in {
      val expected = parse(
        """{"type":"control_request","request_id":"req_1",
          |"request":{"subtype":"interrupt"}}""".stripMargin
      )

      JsonFormats.controlRequestJson("req_1", "interrupt") shouldBe expected
    }

    "spread extra payload fields into the request object alongside subtype" in {
      val result =
        JsonFormats.controlRequestJson("req_2", "mcp_call", Json.obj("tool" -> "foo"))
      val request = (result \ "request").as[JsObject]

      (request \ "subtype").as[String] shouldBe "mcp_call"
      (request \ "tool").as[String] shouldBe "foo"
    }
  }

  "JsonFormats.permissionResultJson" should {

    "render Allow with an updated_input key holding the input to execute" in {
      val result = JsonFormats.permissionResultJson(
        PermissionDecision.Allow(Json.obj("command" -> "ls"))
      )

      (result \ "behavior").as[String] shouldBe "allow"
      (result \ "updated_input").as[JsObject] shouldBe Json.obj("command" -> "ls")
    }

    "render Deny(message, interrupt = true) with all three fields" in {
      JsonFormats.permissionResultJson(
        PermissionDecision.Deny("not allowed", interrupt = true)
      ) shouldBe Json.obj(
        "behavior" -> "deny",
        "message" -> "not allowed",
        "interrupt" -> true
      )
    }

    "render Deny(message) with interrupt explicitly present and false by default" in {
      // Unlike Allow's updated_input, `interrupt` is unconditionally included by
      // permissionResultJson - it is never omitted, only defaulted to false.
      JsonFormats.permissionResultJson(PermissionDecision.Deny("nope")) shouldBe Json.obj(
        "behavior" -> "deny",
        "message" -> "nope",
        "interrupt" -> false
      )
    }
  }

  "JsonFormats.controlResponseJson" should {

    "build a success control_response envelope when a response payload is given" in {
      val expected = parse(
        """{"type":"control_response","response":{"subtype":"success",
          |"request_id":"req_1","response":{"behavior":"allow"}}}""".stripMargin
      )

      JsonFormats.controlResponseJson(
        "req_1",
        response = Some(Json.obj("behavior" -> "allow"))
      ) shouldBe expected
    }

    "build an error control_response envelope when an error message is given" in {
      val expected = parse(
        """{"type":"control_response","response":{"subtype":"error",
          |"request_id":"req_1","error":"boom"}}""".stripMargin
      )

      JsonFormats.controlResponseJson("req_1", error = Some("boom")) shouldBe expected
    }
  }
}
