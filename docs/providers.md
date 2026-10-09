# Services and providers 🧭

[← back to the README](../README.md)

How to obtain a service: the factories, configuration and API keys, and every provider reachable through the OpenAI
interfaces - Azure, Anthropic (direct and Bedrock), Google Gemini and Vertex AI, Mistral, Groq, Grok, Cerebras,
DeepSeek, Perplexity, Liquid AI, the decision models, and any OpenAI-compatible endpoint.

First you need to provide an implicit execution context, e.g., as

```scala
  implicit val ec = ExecutionContext.global
```

A `Materializer`/`ActorSystem` is **not** required to build or call a service - each service
created via a plain factory (e.g. `OpenAIServiceFactory()`) owns and manages its own execution
environment internally, and `service.close()` tears it down together with the HTTP client. You
only need an implicit `Materializer` yourself if you're *consuming* a streamed result (a
`Source[..., akka.NotUsed]` returned by e.g. `createChatCompletionStreamed`), e.g.:

```scala
  implicit val materializer = Materializer(ActorSystem())

  service.createChatCompletionStreamed(...).runWith(Sink.foreach(println))
```

Then you can obtain a service in one of the following ways.

- Default config (expects env. variable(s) to be set as defined in `Config` section)
```scala
  val service = OpenAIServiceFactory()
```

- Custom config
```scala
  val config = ConfigFactory.load("path_to_my_custom_config")
  val service = OpenAIServiceFactory(config)
```

- Without config

```scala
  val service = OpenAIServiceFactory(
     apiKey = "your_api_key",
     orgId = Some("your_org_id") // if you have one
  )
```

- For **Azure** with API Key

```scala
  val service = OpenAIServiceFactory.forAzureWithApiKey(
    resourceName = "your-resource-name",
    deploymentId = "your-deployment-id", // usually model name such as "gpt-5.4"
    apiVersion = "2023-05-15",           // newest version
    apiKey = "your_api_key"
  )
```

- For **Amazon Bedrock**, which exposes the OpenAI **Responses API** and Chat Completions. One entry point, `forBedrock`, with two independent choices: how to authenticate (`auth`) and which host to talk to (`endpoint`). Requires a region (`AWS_BEDROCK_REGION`).

```scala
  // auth defaults to BedrockAuth.fromEnv(): the Bedrock API key (`AWS_BEARER_TOKEN_BEDROCK`)
  // when one is set, otherwise SigV4 with IAM credentials - no branching for dev vs. prod

  // OpenAI provider models (e.g. "openai.gpt-5.5") are served from the `openai/v1` base path
  val service = OpenAIServiceFactory.forBedrock(isOpenAIModel = true)
  service.createModelResponse(Inputs.Text("What is the capital of France?"),
    settings = CreateModelResponseSettings(model = ModelId.bedrock_openai_gpt_5_5))

  // other models (e.g. "openai.gpt-oss-120b") use the standard `v1` base path
  val service = OpenAIServiceFactory.forBedrock()

  // an explicit IAM access key and secret, signed per request (rotating creds are re-read)
  val signed = OpenAIServiceFactory.forBedrock(
    auth = BedrockAuth.SigV4(AwsCredentialsProvider.static(accessKey, secretKey)),
    isOpenAIModel = true)

  // the classic runtime host, which also accepts the cross-region inference profiles
  val runtime = OpenAIServiceFactory.forBedrock(endpoint = BedrockEndpoint.Runtime)
  runtime.createChatCompletion(Seq(UserMessage("Hi")),
    CreateChatCompletionSettings(model = "global.openai.gpt-5.6-luna"))

  // both auth forms also take a shared engine, which the service never closes
  val shared = OpenAIServiceFactory.forBedrockWithEngine(engine, endpoint = BedrockEndpoint.Runtime)
```

- Minimal `OpenAICoreService` supporting `listModels`, `createCompletion`, `createChatCompletion`, and `createEmbeddings` calls - provided e.g. by [FastChat](https://github.com/lm-sys/FastChat) service running on the port 8000

```scala
  val service = OpenAICoreServiceFactory("http://localhost:8000/v1/")
```

-  `OpenAIChatCompletionService` providing solely `createChatCompletion`

1. [Azure AI](https://azure.microsoft.com/en-us/products/ai-studio) - e.g. Cohere R+ model
```scala
  val service = OpenAIChatCompletionServiceFactory.forAzureAI(
    endpoint = sys.env("AZURE_AI_COHERE_R_PLUS_ENDPOINT"),
    region = sys.env("AZURE_AI_COHERE_R_PLUS_REGION"),
    accessToken = sys.env("AZURE_AI_COHERE_R_PLUS_ACCESS_KEY")
  )
```

2. [Anthropic](https://www.anthropic.com/api) - requires `openai-scala-anthropic-client` lib and `ANTHROPIC_API_KEY`
```scala
  val service = AnthropicServiceFactory.asOpenAI() // or AnthropicServiceFactory.bedrockAsOpenAI
```

   **OAuth / bearer token auth (Anthropic)** - as an alternative to `ANTHROPIC_API_KEY`:
   ```scala
     // static bearer/OAuth token, reads ANTHROPIC_AUTH_TOKEN, then CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE,
     // then CLAUDE_CODE_OAUTH_TOKEN
     val service = AnthropicServiceFactory.forAuthToken()

     // `ant auth login` OAuth profile, with automatic token refresh
     val service = AnthropicServiceFactory.forOAuthProfile()
   ```
   Set `ANTHROPIC_AUTH_TOKEN` to a platform OAuth token, e.g. `export ANTHROPIC_AUTH_TOKEN=$(ant auth print-credentials --access-token)`. `forOAuthProfile()` instead resolves an `ant auth login` profile (`ANTHROPIC_PROFILE` / `<config-dir>/active_config` / `default`) and refreshes its token automatically as it expires. Pass `withOAuthBeta = false` on `forAuthToken()` for a gateway-issued static bearer token. **`CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE`** is a safer place to park a fallback token persistently (e.g. in `~/.bashrc`) than `CLAUDE_CODE_OAUTH_TOKEN` itself: the real `claude` CLI reads only the exact literal `CLAUDE_CODE_OAUTH_TOKEN` for its own auth, so exporting that one persistently would silently redirect your interactive `claude` sessions onto it too (ranking above subscription `/login`) - the `_ALTERNATIVE`-suffixed name is invisible to the CLI. **Caveat:** either variant's underlying token (from `claude setup-token`) is a Claude Code subscription token - it's scoped to the Claude Code backend, so treat both as best-effort only. Live 2026-10-07: such a token served Claude Haiku 4.5 through the public API, while every other model answered a 429 `rate_limit_error` "Error" - not a real rate limit, so don't retry it; use an API key for those models. Subscription usage for agents is sanctioned exclusively through the Claude Agent SDK/CLI harness (via the "Agent SDK credit" for Pro/Max/Team/Enterprise plans, introduced 2026-06-15) - not through these REST endpoints.

   **Anthropic on Amazon Bedrock** - all variants read `AWS_BEDROCK_REGION`, static credentials come from `AWS_BEDROCK_ACCESS_KEY` / `AWS_BEDROCK_SECRET_KEY` (+ optional `AWS_SESSION_TOKEN`):
   ```scala
     // SigV4 with static (or env-provided session) credentials
     val service = AnthropicServiceFactory.bedrockAsOpenAI()

     // SigV4 with temporary STS session credentials minted from the access/secret key (default 1h, auto-refreshed)
     val service = AnthropicServiceFactory.bedrockAsOpenAIWithSessionToken()

     // Bedrock API key (`AWS_BEARER_TOKEN_BEDROCK`) - plain bearer auth, no SigV4 signing
     val service = AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken()

     // Bedrock's own batch inference (S3-staged JSONL); returns a batch-capable service - see the Batch section
     val service = AnthropicServiceFactory.bedrockAsOpenAIWithBatchSupport(s3Bucket = "my-bucket", roleArn = "arn:aws:iam::...:role/bedrock-batch")

     // the native AnthropicService against the `bedrock-mantle` Anthropic Messages endpoint (`AWS_BEARER_TOKEN_BEDROCK`)
     val service = AnthropicServiceFactory.forBedrockMantle()
   ```
   Pass `inferenceProfilePrefix = Some("eu")` (or `"us"` / `"global"`) to route through a cross-region inference profile. The native (non-adapter) counterparts are `forBedrock`, `forBedrockWithSessionToken`, and `forBedrockWithBearerToken`.

3. [Google Vertex AI](https://cloud.google.com/vertex-ai) - requires `openai-scala-google-vertexai-client` lib and `VERTEXAI_LOCATION` + `VERTEXAI_PROJECT_ID`
```scala
  val service = VertexAIServiceFactory.asOpenAI()
```

4. [Google Gemini](https://ai.google.dev/) - requires `openai-scala-google-gemini-client` lib and `GOOGLE_API_KEY`
```scala
  val service = GeminiServiceFactory.asOpenAI()
```

5. [Perplexity](https://www.perplexity.ai/) - requires `openai-scala-perplexity-client` lib and `PERPLEXITY_API_KEY` (or `SONAR_API_KEY`)

   🔥 **Agent API** (`POST /v1/agent`, since 1.4.0) - web-grounded runs on presets (`fast`, `low`, `medium`, `high`, `xhigh`,
   `wide-research`) or any `provider/model`, with built-in tools (web search, URL fetch, finance / people search, sandbox, MCP,
   connectors), custom functions, skills, structured output, background runs, typed streaming and sandbox files:
```scala
  import io.cequence.openaiscala.perplexity.domain.agent._

  val service = SonarServiceFactory()

  service
    .createAgentResponse(
      AgentInput("What changed in the EU AI Act this month?"),
      CreateAgentResponseSettings(
        preset = Some(AgentPreset.low),
        tools = Seq(AgentTool.WebSearch(filters = Some(WebSearchFilters(searchRecencyFilter = Some("month")))))
      )
    )
    .map { response =>
      println(response.outputText)
      response.citations.foreach(c => println(c.url))
    }
```
   **OpenAI chat-completion adapter** on the Agent API (via Perplexity's OpenAI-compatible `/v1/responses` alias) - chat,
   function tools, JSON and streaming with `provider/model` ids:
```scala
  val chat = SonarServiceFactory.agentAsOpenAI()
  chat.createChatToolCompletion(messages, tools, settings = CreateChatCompletionSettings("openai/gpt-5.4-mini"))
```
   Perplexity answers `json_object` with an empty `{}`, so pass `jsonSchemaModels = Seq(model)` to `createChatCompletionWithJSON`;
   web search rides on `createChatToolCompletion` / the typed stream with `settings.setResponsesTools(Seq(WebSearchTool()))`.

   Also `createAgentResponseStreamed` (typed `AgentStreamEvent`s), `retrieveAgentResponse`, `resumeAgentResponseStream`
   (reconnect a background stream after a sequence number), `cancelAgentResponse`, `listAgentResponseFiles` /
   `downloadAgentResponseFile` and `listAgentModels`. See
   [PerplexityAgentApiSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/sonar/PerplexityAgentApiSmokeTest.scala).

   ⚠️ Perplexity [retires the Sonar Chat Completions endpoint on 2026-09-27](https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar/overview):
   `createChatCompletion` / `createChatCompletionStreamed` and the OpenAI adapter built on them (`SonarServiceFactory.asOpenAI()`,
   `ChatProviderSettings.sonar`) are `@deprecated` since 1.4.0. Sonar model to preset: `sonar` -> `fast`, `sonar-pro` -> `low`,
   `sonar-reasoning-pro` -> `medium`, `sonar-deep-research` -> `high`.

6. [Novita](https://novita.ai/) - requires `NOVITA_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.novita)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.novita)
```

7. [TypeSafe AI](https://typesafe.ai/) - requires `openai-scala-typesafe-client` lib and `TYPESAFE_API_KEY`
```scala
  import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory

  val typeSafe = TypeSafeServiceFactory()        // native System One service (jev-latest)
  // or as an OpenAIChatCompletionService for json_schema structured output
  val service = TypeSafeServiceFactory.asOpenAI()
```
   System One (`jev`) is a decision model, not a chat model - see [TypeSafe AI (Jev)](decision-models.md#typesafe-ai-jev-) below for the questions / answers API and what the OpenAI adapter does.

   🔥 [Liquid AI](https://www.liquid.ai/)'s decision model **d1** (launched 2026-09-30) serves the same System One API, so the same lib reaches it with `LIQUID_API_KEY`:
```scala
  val d1 = TypeSafeServiceFactory.liquid()              // native System One service (d1:free)
  val d1Service = TypeSafeServiceFactory.liquidAsOpenAI() // json_schema structured output
```
   See [Liquid AI (d1)](decision-models.md#liquid-ai-d1-) below - what works, and d1 vs Jev.

   🔥 Perplexity's multimodal decision model **pplx-decider-v1.1-27b** (the 2026-10-06 update of the 2026-10-01 launch model) takes the same questions - and images - on its Decisions API, with `PERPLEXITY_API_KEY`:
```scala
  val decider = TypeSafeServiceFactory.perplexity()                 // native, POST /v1/decisions
  val deciderService = TypeSafeServiceFactory.perplexityAsOpenAI()  // json_schema structured output, images in user messages
```
   See [Perplexity Decisions API (pplx-decider)](decision-models.md#perplexity-decisions-api-pplx-decider-) below - images, limits, and the decider vs Jev vs d1.

   🔥 Any host of decision models goes through `DecisionProviderSettings`, like `ChatProviderSettings` for chat - e.g. OpenRouter's ten (`OPENROUTER_API_KEY`):
```scala
  val openRouter = TypeSafeServiceFactory(DecisionProviderSettings.openRouter)
  val openRouterService = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.openRouter)
```
   See [Decision-model providers](decision-models.md#decision-model-providers-) below.

8. [Groq](https://wow.groq.com/) - requires `GROQ_API_KEY"`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.groq)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.groq)
```
   Strict `json_schema` and function tools work on the `openai/gpt-oss-*` and `qwen/qwen3.x-27b` models; `allam-2-7b`
   supports neither. The agentic `groq/compound*` systems (which rejected both, running their own built-in tools) are
   gone from Groq's catalog (404 since 2026-10-09; the constants are deprecated). Two Groq-specific behaviours are worth coding for:
   - **The schema is validated server-side, not constrained during decoding.** You never get a non-conforming 200, but a
     generation that breaks the schema comes back as HTTP 400 `json_validate_failed` with the offending text in
     `failed_generation` (reproduced on four of the five schema-capable models with a prompt that fights the schema).
     Treat it as a retryable outcome rather than an unexpected error.
   - **`response_format` cannot be combined with a non-empty `tools` array** on any Groq model: HTTP 400 "json mode
     cannot be combined with tool/function calling". Adding `tool_choice = "none"` makes the request succeed, at the
     cost of never calling a tool.

   Beyond plain function calling, Groq accepts `mcp` tools everywhere and executes `browser_search` and
   `code_interpreter` server-side on the `openai/gpt-oss-*` models. It also serves a Responses API across its chat
   models. Note the two surfaces report provider-executed tools differently: `/chat/completions` returns a non-OpenAI
   `executed_tools` array, while `/responses` emits typed output items instead.

9. [Grok](https://x.ai) - requires `GROK_API_KEY"`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.grok)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.grok)
```

10. [Fireworks AI](https://fireworks.ai/) - requires `FIREWORKS_API_KEY"`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.fireworks)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.fireworks)
```

11. [Octo AI](https://octo.ai/) - requires `OCTOAI_TOKEN`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.octoML)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.octoML)
```

12. [TogetherAI](https://www.together.ai/)  requires `TOGETHERAI_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.togetherAI)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.togetherAI)
```

13. [Cerebras](https://cerebras.ai/)  requires `CEREBRAS_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.cerebras)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.cerebras)
```

14. [Mistral](https://mistral.ai/) requires `MISTRAL_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.mistral)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.mistral)
```
   🔥 **Mistral Large 4** (`NonOpenAIModelId.mistral_large_4`, public preview since 2026-10-06) reasons by default and
   then answers `content` as a list of thinking / text chunks - read as the text (and as `Thinking` + `Text` on the typed
   stream), so the usual calls work unchanged. `reasoning_effort` is a switch on Mistral's reasoning models (Large 4,
   Medium 3.5, Small 4: `none` / `high`; GLM 5.3: `low` / `high` / `max`) and other values are mapped onto it. See
   [MistralLarge4SmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/mistral/MistralLarge4SmokeTest.scala).

15. [Ollama](https://ollama.com/)
```scala
  val service = OpenAIChatCompletionServiceFactory(
    coreUrl = "http://localhost:11434/v1/"
  )
```
or with streaming
```scala
  val service = OpenAIChatCompletionServiceFactory.withStreaming(
    coreUrl = "http://localhost:11434/v1/"
  )
```

16. [MiniMax](https://www.minimax.io/) - requires `MINIMAX_API_KEY`
```scala
  // global endpoint
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.minimax)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.minimax)

  // China endpoint (api.minimaxi.com)
  val chinaService = OpenAIChatCompletionServiceFactory(ChatProviderSettings.minimaxChina)
```
   MiniMax also exposes an Anthropic-compatible endpoint, reachable via the Anthropic client's escape hatch:
```scala
  import io.cequence.wsclient.domain.WsRequestContext

  val anthropicService = AnthropicServiceFactory.customInstance(
    coreUrl = "https://api.minimax.io/anthropic/v1/", // or "https://api.minimaxi.com/anthropic/v1/" for China
    requestContext = WsRequestContext(
      authHeaders = Seq(("Authorization", s"Bearer ${sys.env("MINIMAX_API_KEY")}"))
    )
  )
```
   Claude in **Microsoft Foundry** serves the same Messages API, so the escape hatch reaches it too (the model is your
   deployment's name; live-verified 2026-10-07 with chat, a streamed tool call and `json_schema` output, see
   [AnthropicAccessModesSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/AnthropicAccessModesSmokeTest.scala)):
```scala
  val foundry = AnthropicServiceFactory.asOpenAI(
    AnthropicServiceFactory.customInstance(
      coreUrl = "https://<resource>.services.ai.azure.com/anthropic/v1/",
      requestContext = WsRequestContext(
        authHeaders = Seq("x-api-key" -> sys.env("ANTHROPIC_FOUNDRY_API_KEY"), "anthropic-version" -> "2023-06-01")
      )
    )
  )
```

- Note that services with additional streaming support - `createCompletionStreamed` and `createChatCompletionStreamed` provided by [OpenAIStreamedServiceExtra](../openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIStreamedServiceExtra.scala) (requires `openai-scala-client-stream` lib)

```scala
  import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
  import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._

  val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()
```

similarly for a chat-completion service

```scala
  import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._

  val service = OpenAIChatCompletionServiceFactory.withStreaming(
    coreUrl = "https://api.fireworks.ai/inference/v1/",
    authHeaders = Seq(("Authorization", s"Bearer ${sys.env("FIREWORKS_API_KEY")}"))
  )
```

or only if streaming is required

```scala
  val service: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionStreamedServiceFactory(
      coreUrl = "https://api.fireworks.ai/inference/v1/",
      authHeaders = Seq(("Authorization", s"Bearer ${sys.env("FIREWORKS_API_KEY")}"))
   )
```

- Via dependency injection (requires `openai-scala-guice` lib)

```scala
  class MyClass @Inject() (openAIService: OpenAIService) {...}
```

---

**✔️ Important**: After you are done using the service, you should close it by calling `service.close`. Otherwise, the underlying resources/threads won't be released.

---
