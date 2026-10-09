# Streaming 🌊

[← back to the README](../README.md)

OpenAI-shaped streams, the provider-neutral typed stream (`ChatChunk`), the stream frame cap, and human approval
of tool calls mid-stream.

- **Typed streaming** - `createChatToolCompletionStreamed` (and the tool-less alias `createChatCompletionStreamedTyped`)
  returns `Source[ChatChunk, NotUsed]`, a provider-neutral sealed hierarchy that OpenAI (and every OpenAI-compatible provider),
  Anthropic (direct and Bedrock) and Google Gemini / Vertex AI all map onto, so the same pattern match works everywhere:

  | `ChatChunk` | Meaning | OpenAI / compatible | Anthropic | Gemini / Vertex AI |
  |---|---|---|---|---|
  | `Start(id, model)` | first chunk | chunk id / model | `message_start` | `modelVersion` |
  | `Text(text)` | answer fragment | `delta.content` | `text_delta` | text part |
  | `Thinking(text)` | reasoning fragment | `delta.reasoning_content` (DeepSeek, Grok, ...) / `delta.reasoning` (Groq) | `thinking_delta` (summarized thinking is requested automatically) | thought-summary part (`includeThoughts` on by default) |
  | `ThinkingSignature(signature, callId)`, `RedactedThinking` | opaque data to echo back in tool loops (`callId` set when the signature rides on a function call) | encrypted reasoning (Responses API) | `signature_delta`, `redacted_thinking` | `thoughtSignature` |
  | `ToolCallStart` / `ToolCallDelta` / `ToolCall` | tool call: start, argument fragments, assembled call | `delta.tool_calls` fragments | `tool_use` + `input_json_delta`; `server_tool_use` / `mcp_tool_use` (`serverSide = true`, MCP servers via `setAnthropicMcpServers`) | `functionCall` (complete); `executableCode` and `mcpServers` calls (`serverSide = true`) |
  | `ToolResult` | result of a provider-executed tool | code interpreter outputs, MCP / file search results (Responses API) | web search / web fetch / code execution / bash / text editor / MCP result blocks | `codeExecutionResult`; `functionResponse` of `mcpServers` calls |
  | `CodeExecution` / `CodeExecutionResult` | semantic view of server-side code runs (emitted in addition to the tool layer, same `callId`) | `code_interpreter_call` (Responses API) | `code_execution` / `bash_code_execution` | `executableCode` / `codeExecutionResult` |
  | `WebSearch` / `WebSearchResult` | semantic view of server-side web searches | `web_search_call` (Responses API) | `web_search` server tool | Google Search grounding queries / chunks |
  | `Image` | generated / returned images (base64 or URL) | image generation partial & final images, code interpreter image outputs (Responses API) | - | inline image parts |
  | `Refusal` | refusal text | `refusal` deltas (Responses API) | - | - |
  | `Citation` | citation / grounding reference | output-text annotations (Responses API) | `citations_delta` | `groundingMetadata` |
  | `ToolApprovalRequest(requestId, toolName, arguments, serverName, runId)` | a tool call the run waits to have approved (see **Human approval** below) | `mcp_approval_request` (Responses API) | Managed Agents tool with an `always_ask` policy | - |
  | `Finish(reason, providerReason)` | normalized stop reason (`stop`, `tool_calls`, `length`, `content_filter`, `approval_required`, `unknown`) + the provider's own | `finish_reason` | `message_delta.stop_reason` | `finishReason` |
  | `Usage(usage)` | OpenAI-shaped usage | trailing usage chunk (`stream_options.include_usage` is requested unless you set `stream_options` in `extra_params`) | merged `message_start` + `message_delta` usage | `usageMetadata` |
  | `Other(kind, raw)` | anything unmodeled (never dropped) | further choices | `ping`, unknown events / blocks | further candidates, unknown parts |

```scala
  import io.cequence.openaiscala.domain.response.ChatChunk._

  val weather = FunctionTool(name = "get_weather", parameters = JsonSchema.Object(
    properties = Seq("location" -> JsonSchema.String()), required = Seq("location")))

  service
    .createChatToolCompletionStreamed(
      messages = Seq(UserMessage("What is the weather in Oslo? Use get_weather.")),
      tools = Seq(weather),
      settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_5, reasoning_effort = Some(ReasoningEffort.medium))
    )
    .runWith(Sink.foreach {
      case Thinking(t)                       => print(s"[thinking] $t")
      case Text(t)                           => print(t)
      case ToolCall(_, id, name, args, _)    => println(s"call $name($args) -> $id")
      case ToolResult(_, name, _, text, _)   => println(s"$name returned ${text.getOrElse("")}")
      case Finish(reason, _)                 => println(s"done: $reason")
      case _                                 => ()
    })
```

  `source.texts` is the legacy text-only view (`thinkingTexts`, `toolCalls`, `toolResults`, `citations`, `codeExecutions`,
  `webSearches`, `images` likewise), and `source.assembled` folds the stream into an `AssembledChatCompletion` whose
  `toAssistantToolMessage` is the assistant turn of a tool loop - append it plus one `ToolMessage` per `clientToolCalls` entry,
  then call again. Every chunk also has a JSON `Format` (`"type"`-keyed) for logging and replay.

  Provider-native server-side tools ride along via `setAnthropicTools` / `setGeminiTools` / `setVertexAITools` /
  `setResponsesTools` and come back as `ToolResult`s; `setGeminiIncludeThoughts(false)` and `setVertexAIIncludeThoughts(false)`
  turn thought summaries off. Anthropic's **MCP connector** rides along the same way: `setAnthropicMcpServers(Seq(MCPServerURLDefinition(name, url)))`
  puts remote MCP servers on the request (typed stream and plain calls alike), their `mcp_tool_use` / `mcp_tool_result` arrive as
  server-side `ToolCall`s / `ToolResult`s, and a `pause_turn` (a tool run that outlived the turn budget) is continued transparently
  on the same `Source` - one `Start`, one final `Finish`, one summed `Usage`; `setAnthropicMaxContinuations` caps it (default 6). See
  [AnthropicCreateChatToolCompletionStreamedWithMCPServers](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/AnthropicCreateChatToolCompletionStreamedWithMCPServers.scala).
  (Anthropic API only - Bedrock rejects `mcp_servers`.)

  **Provider-neutral MCP servers and skills** - instead of the provider-specific settings above, pass
  `ChatCompletionTool.MCPServerTool` / `ChatCompletionTool.SkillTool` in `tools` next to your function tools, on
  `createChatToolCompletion` and `createChatToolCompletionStreamed` alike; each adapter maps them onto its native feature or
  fails loudly rather than dropping them:

  | | OpenAI | Anthropic | Gemini | Vertex AI |
  |---|---|---|---|---|
  | `MCPServerTool(name, url, authorizationToken, headers, allowedTools, description, timeout, requireApproval)` | Responses `mcp` tool (the request is routed through the Responses API on any model; approval never required unless asked) | MCP connector `mcp_servers` (bearer token + allowed tools; custom `headers` are refused) | `mcpServers` (bearer as an `Authorization` header, `timeout`; `allowedTools` warned and ignored; Gemini cannot combine it with ANY other tool type, incl. function tools - the adapter fails fast) | refused |
  | `SkillTool(skillId, version, source)` | hosted `shell` tool, `container_auto` environment with `skill_reference`s (GPT-6) | `container.skills` (`Provider` = Anthropic's built-in skills, `Custom` = uploaded; the code execution tool is added) | refused | refused |

```scala
  import io.cequence.openaiscala.domain.ChatCompletionTool.{MCPServerTool, SkillTool, SkillSource}

  service.createChatToolCompletionStreamed(
    messages = Seq(UserMessage("What is the 'given' keyword for in Scala 3? Check scala/scala3.")),
    tools = Seq(
      MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp"),          // the provider calls it
      SkillTool("pptx", version = Some("latest"), source = SkillSource.Provider) // Anthropic's built-in skill
    ),
    settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_5)
  )
```
  See [CreateChatToolCompletionStreamedWithMCPServerTool](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamedWithMCPServerTool.scala)
  (one DeepWiki server on OpenAI, Anthropic and Gemini) and
  [AnthropicCreateChatToolCompletionWithSkillTool](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/skills/AnthropicCreateChatToolCompletionWithSkillTool.scala).
  Gemini's native MCP (`setGeminiTools(Seq(Tool.McpServers(...)))`, live-verified 2026-09-16 with Exa, the GitHub Copilot MCP and
  DeepWiki, alone and several per request, authenticated with `x-api-key` or `Authorization: Bearer` transport headers alike): Gemini
  runs the tools itself; Gemini 2.5 streams each call as a server-side `ToolCall` (named `<server>_<tool>`, at times by the bare tool
  name - with MCP servers configured every call that is not one of your declared function tools counts as theirs) plus a `ToolResult`,
  Gemini 3 echoes no call / result parts at all. Its executor fails transiently (HTTP 500 / 503, or a stream that ends right after the call) -
  the adapter surfaces that as a `ToolResult(isError = true)` on the stream and as a `GeminiScalaMcpCallNotExecutedException` (a
  server-error subtype, so `Retryable` through the adapter - the retry adapter re-issues it) on the plain call instead of an empty answer. See
  [GoogleGeminiCreateChatToolCompletionStreamedWithMcpServers](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/googlegemini/GoogleGeminiCreateChatToolCompletionStreamedWithMcpServers.scala).

  GPT-5.6 and GPT-6 Sol / Luna accept function tools on the chat completions API only with `reasoning_effort = none`, and GPT-6
  Astra and GPT-6.1 Sol only on the Responses API - so the full `OpenAIServiceFactory()` / `.withStreaming()` service routes their
  tool calls and typed tool streams through the Responses API automatically, which keeps the requested reasoning (only an explicit
  `reasoning_effort = none` stays on chat completions). A chat-only service forces `none` instead (Astra / 6.1 Sol fail fast).
  The same routing serves `settings.setResponsesReasoningMode(ReasoningMode.pro)` (GPT-6 pro mode),
  `settings.setResponsesMultiAgent()` (multi-agent execution) and `service_tier = ServiceTier.ultrafast` (the Ultrafast
  tier, which only the Responses API serves) on every chat entry point. Grok, Groq, Cerebras, Fireworks and DeepSeek use the generic mapping (Groq's per-chunk `usage` is
  emitted once); Sonar and Managed Agents stream text / reasoning / finish / usage but reject tools.

  **Responses API streaming** - `createModelResponseStreamed(inputs, settings)` on the streamed OpenAI service returns
  `Source[ResponseStreamEvent, NotUsed]` with every server-sent event typed (`ResponseCreated`, `OutputItemAdded/Done`,
  `OutputTextDelta`, `ReasoningSummaryTextDelta`, `FunctionCallArgumentsDelta/Done`, `CodeInterpreterCodeDelta/Done`,
  `ImageGenerationPartialImage`, `OutputTextAnnotationAdded`, `ResponseCompleted/Incomplete/Failed`, `ToolCallStatus` for the
  lifecycle notifications, `UnknownEvent` for the rest), and `createModelResponseStreamedTyped` renders the same stream as `ChatChunk`s -
  reasoning summaries as `Thinking`, the encrypted reasoning as `ThinkingSignature`, web search / code interpreter / MCP / file search /
  image generation as tool-layer chunks plus `WebSearch`, `CodeExecution`, `CodeExecutionResult`, `Image`, annotations as `Citation`.
  A `response.failed` or `error` event fails the stream. See
  [CreateModelResponseStreamed](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/responsesapi/CreateModelResponseStreamed.scala).

  The chat-completion-shaped way to use it - the same messages + `CreateChatCompletionSettings` inputs as
  `createChatToolCompletionStreamed`, exactly like the Anthropic and Gemini `asOpenAI()` adapters expose natively - is available on the
  streamed OpenAI service via `OpenAIStreamedServiceImplicits._`:

```scala
  import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._

  val service = OpenAIServiceFactory.withStreaming()

  service
    .createChatToolCompletionStreamedViaResponses(
      messages = Seq(UserMessage("Search the news about Jupiter missions, then call get_weather for Oslo.")),
      tools = Seq(weather),
      settings = CreateChatCompletionSettings(ModelId.gpt_5_5, reasoning_effort = Some(ReasoningEffort.low))
        .setResponsesTools(Seq(WebSearchTool(), CodeInterpreterTool(container = CodeInterpreterContainer.Auto())))
    )
    .runWith(Sink.foreach(println))

  // or as a reusable chat-completion service (sync + typed streamed) served by the Responses API
  val chatViaResponses: OpenAIChatCompletionStreamedService = service.responsesAsChatCompletion
```

  Reasoning summaries are requested automatically when `reasoning_effort` is set (`setResponsesReasoningSummary(false)` opts out), and
  `setResponsesTools` adds Responses-native tools (web search, code interpreter, file search, MCP, image generation) whose activity
  arrives as server-side tool-layer chunks plus `WebSearch`, `CodeExecution`, `Image`, .... `service.responsesAsChatCompletion` also
  exists on a plain (non-streaming) `OpenAIService` through `OpenAIChatCompletionExtra._` for the synchronous adapter. See
  [CreateChatToolCompletionStreamedViaResponses](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/responsesapi/CreateChatToolCompletionStreamedViaResponses.scala).
  See [CreateChatToolCompletionStreamed](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamed.scala),
  [AnthropicCreateChatToolCompletionStreamedWithOpenAIAdapter](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/AnthropicCreateChatToolCompletionStreamedWithOpenAIAdapter.scala),
  and [GoogleGeminiCreateChatToolCompletionStreamedWithOpenAIAdapter](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/googlegemini/GoogleGeminiCreateChatToolCompletionStreamedWithOpenAIAdapter.scala).

- **Human approval mid-stream** (🔥 New) - a run that pauses until a tool call is approved - an OpenAI MCP tool with
  `requireApproval` (Responses API) or an Anthropic Managed Agents tool with an `always_ask` permission policy - ends its
  typed stream with one `ToolApprovalRequest` per pending call and `Finish(approval_required)`. Answer on the next call with
  `settings.setToolApprovalDecisions(Seq(request.approve))` (or `request.deny(reason)`), or let a callback decide and get
  ONE stream from the question to the final answer:

```scala
  val service = OpenAIServiceFactory.withStreaming()

  service
    .createChatToolCompletionStreamedWithApprovals(
      messages = Seq(UserMessage("Using deepwiki on repo cequence-io/openai-scala-client: which ws-client version does it use?")),
      tools = Seq(
        ChatCompletionTool.MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp", requireApproval = true)
      ),
      settings = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
    ) { request =>
      println(s"approving ${request.toolName}(${request.arguments})")
      Future.successful(request.approve)
    }
    .runWith(Sink.foreach(println))
```

  A resumed OpenAI run continues the stored response by id, so reasoning and executed MCP calls carry over and a run may pause
  several times; everything that cannot pause refuses such a tool instead of running it unapproved. See
  [CreateChatToolCompletionStreamedWithApproval](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamedWithApproval.scala)
  (resuming by hand) and
  [CreateChatToolCompletionStreamedWithApprovalCallback](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamedWithApprovalCallback.scala).
