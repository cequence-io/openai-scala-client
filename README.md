# OpenAI Scala Client 🤖
[![version](https://img.shields.io/badge/version-1.3.0-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT) ![GitHub Stars](https://img.shields.io/github/stars/cequence-io/openai-scala-client?style=social) [![Follow on X](https://img.shields.io/badge/X-%400xbnd-black?logo=x)](https://x.com/0xbnd) ![GitHub CI](https://github.com/cequence-io/openai-scala-client/actions/workflows/continuous-integration.yml/badge.svg)

This is a no-nonsense async Scala client for OpenAI API and multiple LLM providers supporting all the available endpoints and params **including streaming** (with a 🔥 new provider-neutral typed stream of text / thinking / tool-call / tool-result chunks), **chat completion**, **responses API**, **assistants API**, **tools** (including MCP), **graders**, **vision** (with provider-uniform file/image attachments), **batch processing**, and **voice routines** (as defined [here](https://platform.openai.com/docs/api-reference)), provided in a single, convenient service called [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala) with adapters for Anthropic (incl. Bedrock and Managed Agents), Google Gemini/Vertex AI, Groq, Perplexity, TypeSafe AI (Jev) (🔥 New), and others. The supported calls are:

* **Models**: [listModels](https://platform.openai.com/docs/api-reference/models/list), and [retrieveModel](https://platform.openai.com/docs/api-reference/models/retrieve)
* **Completions**: [createCompletion](https://platform.openai.com/docs/api-reference/completions/create) (deprecated on `OpenAIService` - OpenAI shuts down its last completions models on 2026-09-28; OpenAI-compatible servers keep it via `OpenAICoreService`)
* **Chat Completions**: [createChatCompletion](https://platform.openai.com/docs/api-reference/chat/create), [createChatFunCompletion](https://platform.openai.com/docs/api-reference/chat/create) (deprecated), [createChatToolCompletion](https://platform.openai.com/docs/api-reference/chat/create), and [createChatWebSearchCompletion](https://platform.openai.com/docs/guides/tools-web-search?api-mode=chat)
* **Edits**: [createEdit](https://platform.openai.com/docs/api-reference/edits/create) (deprecated - the endpoint is gone)
* **Images**: [createImage](https://platform.openai.com/docs/api-reference/images/create), [createImageEdit](https://platform.openai.com/docs/api-reference/images/create-edit), and [createImageVariation](https://platform.openai.com/docs/api-reference/images/create-variation) (deprecated - the endpoint is gone with dall-e-2; defaults now use `gpt-image-2`)
* **Embeddings**: [createEmbeddings](https://platform.openai.com/docs/api-reference/embeddings/create)
* **Batches**: [createBatch](https://platform.openai.com/docs/api-reference/batch/create), [retrieveBatch](https://platform.openai.com/docs/api-reference/batch/retrieve), [cancelBatch](https://platform.openai.com/docs/api-reference/batch/cancel), and [listBatches](https://platform.openai.com/docs/api-reference/batch/list), plus the helpers `uploadBatchFile`, `buildAndUploadBatchFile`, `buildBatchFileContent`, `retrieveBatchFile`, `retrieveBatchFileContent`, and `retrieveBatchResponses`
* **Audio**: [createAudioTranscription](https://platform.openai.com/docs/api-reference/audio/createTranscription), [createAudioTranslation](https://platform.openai.com/docs/api-reference/audio/createTranslation) (deprecated - whisper-1, its only model, shuts down 2027-02-26), and [createAudioSpeech](https://platform.openai.com/docs/api-reference/audio/createSpeech)
* **Files**: [listFiles](https://platform.openai.com/docs/api-reference/files/list), [uploadFile](https://platform.openai.com/docs/api-reference/files/upload), [deleteFile](https://platform.openai.com/docs/api-reference/files/delete), [retrieveFile](https://platform.openai.com/docs/api-reference/files/retrieve), [retrieveFileContent](https://platform.openai.com/docs/api-reference/files/retrieve-content), and `retrieveFileContentAsSource` (streamed)
* **Fine-tunes**: [createFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/create), [listFineTunes](https://platform.openai.com/docs/api-reference/fine-tunes/list), [retrieveFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/retrieve), [cancelFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/cancel), [listFineTuneEvents](https://platform.openai.com/docs/api-reference/fine-tunes/events), [listFineTuneCheckpoints](https://platform.openai.com/docs/api-reference/fine-tuning/list-checkpoints), and [deleteFineTuneModel](https://platform.openai.com/docs/api-reference/fine-tunes/delete-model)
* **Moderations**: [createModeration](https://platform.openai.com/docs/api-reference/moderations/create)
* ⚠️ **Assistants, Threads, Thread Messages, Runs and Run Steps** are deprecated since 1.3.1 - OpenAI shut the Assistants API down on 2026-08-26; use the Responses API (`createModelResponse` / `createModelResponseStreamed`) instead
* **Assistants**: [createAssistant](https://platform.openai.com/docs/api-reference/messages/createMessage), [listAssistants](https://platform.openai.com/docs/api-reference/assistants/listAssistants), [retrieveAssistant](https://platform.openai.com/docs/api-reference/assistants/retrieveAssistant), [modifyAssistant](https://platform.openai.com/docs/api-reference/assistants/modifyAssistant), [deleteAssistant](https://platform.openai.com/docs/api-reference/assistants/deleteAssistant), and `deleteAssistantFile`
* **Threads**: [createThread](https://platform.openai.com/docs/api-reference/threads/createThread), [retrieveThread](https://platform.openai.com/docs/api-reference/threads/getThread), [modifyThread](https://platform.openai.com/docs/api-reference/threads/modifyThread), and [deleteThread](https://platform.openai.com/docs/api-reference/threads/deleteThread)
* **Thread Messages**: [createThreadMessage](https://platform.openai.com/docs/api-reference/assistants/createAssistant), [retrieveThreadMessage](https://platform.openai.com/docs/api-reference/messages/getMessage), [modifyThreadMessage](https://platform.openai.com/docs/api-reference/messages/modifyMessage), [listThreadMessages](https://platform.openai.com/docs/api-reference/messages/listMessages), [retrieveThreadMessageFile](https://platform.openai.com/docs/api-reference/messages/getMessageFile), [listThreadMessageFiles](https://platform.openai.com/docs/api-reference/messages/listMessageFiles), and [deleteThreadMessage](https://platform.openai.com/docs/api-reference/messages/deleteMessage)
* **Runs**: [createRun](https://platform.openai.com/docs/api-reference/runs/createRun), [createThreadAndRun](https://platform.openai.com/docs/api-reference/runs/createThreadAndRun), [listRuns](https://platform.openai.com/docs/api-reference/runs/listRuns), [retrieveRun](https://platform.openai.com/docs/api-reference/runs/retrieveRun), [modifyRun](https://platform.openai.com/docs/api-reference/runs/modifyRun), [submitToolOutputs](https://platform.openai.com/docs/api-reference/runs/submitToolOutputs), and [cancelRun](https://platform.openai.com/docs/api-reference/runs/cancelRun)
* **Run Steps**: [listRunSteps](https://platform.openai.com/docs/api-reference/run-steps/listRunSteps), and [retrieveRunStep](https://platform.openai.com/docs/api-reference/run-steps/getRunStep) 
* **Vector Stores**: [createVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/create), [listVectorStores](https://platform.openai.com/docs/api-reference/vector-stores/list), [retrieveVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/retrieve), [modifyVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/modify), and [deleteVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/delete)
* **Vector Store Files**: [createVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/createFile), [listVectorStoreFiles](https://platform.openai.com/docs/api-reference/vector-stores-files/listFiles), [retrieveVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/getFile), and [deleteVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/deleteFile)  
* **Responses**: [createModelResponse](https://platform.openai.com/docs/api-reference/responses/create) (🔥 with tools support), [getModelResponse](https://platform.openai.com/docs/api-reference/responses/get), [deleteModelResponse](https://platform.openai.com/docs/api-reference/responses/delete), [cancelModelResponse](https://platform.openai.com/docs/api-reference/responses/cancel), [getModelResponseInputTokenCounts](https://platform.openai.com/docs/api-reference/responses/token-counts), [listModelResponseInputItems](https://platform.openai.com/docs/api-reference/responses/input-items), and [createModelResponseStreamed](https://platform.openai.com/docs/api-reference/responses-streaming) (🔥 new, typed events or `ChatChunk`s)
* **Graders**: [runGrader](https://platform.openai.com/docs/api-reference/graders/run), and [validateGrader](https://platform.openai.com/docs/api-reference/graders/validate)

The Anthropic client additionally covers the native **Messages** (incl. typed stream events), **Message Batches**, **Files**, **Skills**, and the whole **Managed Agents** API surface (agents, environments and their work queue, sessions, deployments, vaults and credentials, memory stores) - see the [Anthropic Managed Agents](#anthropic-managed-agents) section below.

Note that in order to be consistent with the OpenAI API naming, the service function names match exactly the API endpoint titles/descriptions in camelCase.
Also, we aimed for the library to be self-contained with the fewest dependencies possible. Therefore, we implemented our own generic WS client (currently with Play WS backend, which can be swapped for other engines in the future). Additionally, if dependency injection is required, we use the `scala-guice` library.

---

👉 **No time to read a lengthy tutorial? Sure, we hear you! Check out the [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples) to see how to use the lib in practice.**

👉 **Follow [@0xbnd](https://x.com/0xbnd) on X for release announcements and LLM provider news.**

---

In addition to OpenAI, this library supports many other LLM providers. For providers that aren't natively compatible with the chat completion API, we've implemented adapters to streamline integration (see [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples)).

| Provider | JSON/Structured Output | Tools Support                     | Batch (🔥 New)          | Description |
|----------|------------------------|-----------------------------------|-------------------------|-------------|
| [OpenAI](https://platform.openai.com) | Full                   | Standard + Responses API          | Yes                     | Full API support |
| [Azure OpenAI](https://azure.microsoft.com/en-us/products/ai-services/openai-service) | Full                   | Standard + Responses API          | Yes                     | OpenAI on Azure|
| [Anthropic](https://www.anthropic.com/api) | Full (🔥 New)          | Yes, also MCP and Skills (🔥 New) | Yes                     | Claude models |
| [Anthropic Bedrock](https://aws.amazon.com/bedrock/claude/) | Full (🔥 New)          | Yes, also MCP (🔥 New)            | Yes (no prompt caching) | Claude on AWS |
| [OpenAI Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html) | Full (🔥 New)          | Standard + Responses API          |                         | GPT-5.x, gpt-oss, Grok & more on AWS (`bedrock-mantle` / `bedrock-runtime`) |
| [Azure AI](https://azure.microsoft.com/en-us/products/ai-studio) | Varies                 |                                   |                         | Open-source models |
| [Cerebras](https://cerebras.ai/) | Full (`gpt-oss-120b`, `qwen-3.8-27b`) | Yes                  |                         | Fast inference |
| [Deepseek](https://deepseek.com/) | Only JSON object mode  |                                   |                         | Chinese provider |
| [FastChat](https://github.com/lm-sys/FastChat) | Varies                 |                                   |                         | Local LLMs |
| [Fireworks AI](https://fireworks.ai/) | Full (🔥 New)           |                                   |                         | Cloud provider |
| [Google Gemini](https://ai.google.dev/) | Full                   | Yes (🔥 New)                      | Yes                     | Google's models |
| [Google Vertex AI](https://cloud.google.com/vertex-ai) | Full                   | Yes                               | Yes                     | Gemini models |
| [Grok](https://x.ai/) | Full                   | Yes                               |                         | x.AI models |
| [Groq](https://wow.groq.com/) | Full (`openai/gpt-oss-*`, `qwen/qwen3.x-27b`) | Yes, also MCP and server-side tools | Yes                     | Fast inference |
| [MiniMax](https://www.minimax.io/) | Varies (`json_schema` on M2.7+ per docs)|                                   |                         | Chinese provider (global & China) |
| [Mistral](https://mistral.ai/) | Full (🔥 New)           |                                   |                         | Open-source leader |
| [Novita](https://novita.ai/) | Full (model-dependent) |                                   |                         | Cloud provider |
| [Octo AI](https://octo.ai/) | Only JSON object mode  |                                   |                         | Cloud provider (obsolete) |
| [Ollama](https://ollama.com/) | Varies                 |                                   |                         | Local LLMs |
| [Perplexity](https://www.perplexity.ai/) | Only implied           |                                   |                         | Agent API (🔥 1.3.1) + Sonar (⚠️ chat completions retire on 2026-09-27, see below) |
| [TogetherAI](https://www.together.ai/) | Full (🔥 New, model-dependent)|                                   |                         | Cloud provider |
| [TypeSafe AI](https://typesafe.ai/) (🔥 New) | Typed by construction  | `json_schema` structured output only (`asOpenAI()`) |                         | Decision model `Jev`: typed answers with calibrated probabilities |

---

👉 For background information how the project started read an article about the lib/client on [Medium](https://medium.com/@0xbnd/openai-scala-client-is-out-d7577de934ad).

Also try out our [Scala client for Pinecone vector database](https://github.com/cequence-io/pinecone-scala), or use both clients together! [This demo project](https://github.com/cequence-io/pinecone-openai-scala-demo) shows how to generate and store OpenAI embeddings into Pinecone and query them afterward. The OpenAI + Pinecone combo is commonly used for autonomous AI agents, such as [babyAGI](https://github.com/yoheinakajima/babyagi) and [AutoGPT](https://github.com/Significant-Gravitas/Auto-GPT).

**✔️ Important**: this is a "community-maintained" library and, as such, has no relation to OpenAI company.

## Installation 🚀

The currently supported Scala versions are **2.12, 2.13**, and **3**.  

To install the library, add the following dependency to your *build.sbt*

```
"io.cequence" %% "openai-scala-client" % "1.3.0"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>io.cequence</groupId>
    <artifactId>openai-scala-client_2.12</artifactId>
    <version>1.3.0</version>
</dependency>
```

If you want streaming support, use `"io.cequence" %% "openai-scala-client-stream" % "1.3.0"` instead.

For a single dependency that includes all provider clients (Anthropic, Gemini, Vertex AI, Perplexity, TypeSafe AI, token counting):

```
"io.cequence" %% "openai-scala-all" % "1.3.0"
```

## Config ⚙️

- Env. variables: `OPENAI_SCALA_CLIENT_API_KEY` and optionally also `OPENAI_SCALA_CLIENT_ORG_ID` (if you have one)
- File config (default):  [openai-scala-client.conf](./openai-client/src/main/resources/openai-scala-client.conf)

## Usage 👨‍🎓

**I. Obtaining OpenAIService**

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
   Set `ANTHROPIC_AUTH_TOKEN` to a platform OAuth token, e.g. `export ANTHROPIC_AUTH_TOKEN=$(ant auth print-credentials --access-token)`. `forOAuthProfile()` instead resolves an `ant auth login` profile (`ANTHROPIC_PROFILE` / `<config-dir>/active_config` / `default`) and refreshes its token automatically as it expires. Pass `withOAuthBeta = false` on `forAuthToken()` for a gateway-issued static bearer token. **`CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE`** is a safer place to park a fallback token persistently (e.g. in `~/.bashrc`) than `CLAUDE_CODE_OAUTH_TOKEN` itself: the real `claude` CLI reads only the exact literal `CLAUDE_CODE_OAUTH_TOKEN` for its own auth, so exporting that one persistently would silently redirect your interactive `claude` sessions onto it too (ranking above subscription `/login`) - the `_ALTERNATIVE`-suffixed name is invisible to the CLI. **Caveat:** either variant's underlying token (from `claude setup-token`) is a Claude Code subscription token - it's scoped to the Claude Code backend and documented as rejected by the public API (expect a 401), so treat both as best-effort only. Subscription usage for agents is sanctioned exclusively through the Claude Agent SDK/CLI harness (via the "Agent SDK credit" for Pro/Max/Team/Enterprise plans, introduced 2026-06-15) - not through these REST endpoints.

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

   🔥 **Agent API** (`POST /v1/agent`, since 1.3.1) - web-grounded runs on presets (`fast`, `low`, `medium`, `high`, `xhigh`,
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
   [PerplexityAgentApiSmokeTest](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/sonar/PerplexityAgentApiSmokeTest.scala).

   ⚠️ Perplexity [retires the Sonar Chat Completions endpoint on 2026-09-27](https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar/overview):
   `createChatCompletion` / `createChatCompletionStreamed` and the OpenAI adapter built on them (`SonarServiceFactory.asOpenAI()`,
   `ChatProviderSettings.sonar`) are `@deprecated` since 1.3.1. Sonar model to preset: `sonar` -> `fast`, `sonar-pro` -> `low`,
   `sonar-reasoning-pro` -> `medium`, `sonar-deep-research` -> `high`.

6. [Novita](https://novita.ai/) - requires `NOVITA_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.novita)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.novita)
```

7. [TypeSafe AI](https://typesafe.ai/) (🔥 New) - requires `openai-scala-typesafe-client` lib and `TYPESAFE_API_KEY`
```scala
  import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory

  val typeSafe = TypeSafeServiceFactory()        // native System One service (jev-latest)
  // or as an OpenAIChatCompletionService for json_schema structured output
  val service = TypeSafeServiceFactory.asOpenAI()
```
   System One (`jev`) is a decision model, not a chat model - see [TypeSafe AI (Jev)](#typesafe-ai-jev-) below for the questions / answers API and what the OpenAI adapter does.

8. [Groq](https://wow.groq.com/) - requires `GROQ_API_KEY"`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.groq)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.groq)
```
   Strict `json_schema` and function tools work on the `openai/gpt-oss-*` and `qwen/qwen3.x-27b` models; the agentic
   `groq/compound*` models reject both a `tools` array and `json_schema` (they run their own built-in tools instead),
   and `allam-2-7b` supports neither. Two Groq-specific behaviours are worth coding for:
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

- Note that services with additional streaming support - `createCompletionStreamed` and `createChatCompletionStreamed` provided by [OpenAIStreamedServiceExtra](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIStreamedServiceExtra.scala) (requires `openai-scala-client-stream` lib)

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

**II. Calling functions**

Full documentation of each call with its respective inputs and settings is provided in [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala). Since all the calls are async they return responses wrapped in `Future`.

There is a new project [openai-scala-client-examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples) where you can find a lot of ready-to-use examples!

- List models

```scala
  service.listModels.map(models =>
    models.foreach(println)
  )
```

- Retrieve model
```scala
  service.retrieveModel(ModelId.gpt_5_5).map(model =>
    println(model.getOrElse("N/A"))
  )
```

- Create chat completion 

```scala
  val createChatCompletionSettings = CreateChatCompletionSettings(
    model = ModelId.gpt_5_5
  )

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("Who won the world series in 2020?"),
    AssistantMessage("The Los Angeles Dodgers won the World Series in 2020."),
    UserMessage("Where was it played?"),
  )

  service.createChatCompletion(
    messages = messages,
    settings = createChatCompletionSettings
  ).map { chatCompletion =>
    println(chatCompletion.contentHead)
  }
```

- Create chat completion for functions 

```scala
  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?")
  )

  // as a param type we can use "number", "string", "boolean", "object", "array", and "null"
  val tools = Seq(
    FunctionSpec(
      name = "get_current_weather",
      description = Some("Get the current weather in a given location"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "location" -> Map(
            "type" -> "string",
            "description" -> "The city and state, e.g. San Francisco, CA"
          ),
          "unit" -> Map(
            "type" -> "string",
            "enum" -> Seq("celsius", "fahrenheit")
          )
        ),
        "required" -> Seq("location")
      )
    )
  )

  // if we want to force the model to use the above function as a response
  // we can do so by passing: responseToolChoice = Some("get_current_weather")`
  service.createChatToolCompletion(
    messages = messages,
    tools = tools,
    responseToolChoice = None, // means "auto"
    settings = CreateChatCompletionSettings(ModelId.gpt_5_5)
  ).map { response =>
    val chatFunCompletionMessage = response.choices.head.message
    val toolCalls = chatFunCompletionMessage.tool_calls.collect {
      case (id, x: FunctionCallSpec) => (id, x)
    }

    println(
      "tool call ids                : " + toolCalls.map(_._1).mkString(", ")
    )
    println(
      "function/tool call names     : " + toolCalls.map(_._2.name).mkString(", ")
    )
    println(
      "function/tool call arguments : " + toolCalls.map(_._2.arguments).mkString(", ")
    )
  }
```

- Create chat completion with **JSON/structured output**

```scala
  val messages = Seq(
    SystemMessage("Give me the most populous capital cities in JSON format."),
    UserMessage("List only african countries")
  )

  val capitalsSchema = JsonSchema.Object(
    properties = Map(
      "countries" -> JsonSchema.Array(
        items = JsonSchema.Object(
          properties = Map(
            "country" -> JsonSchema.String(
              description = Some("The name of the country")
            ),
            "capital" -> JsonSchema.String(
              description = Some("The capital city of the country")
            )
          ),
          required = Seq("country", "capital")
        )
      )
    ),
    required = Seq("countries")
  )

  val jsonSchemaDef = JsonSchemaDef(
    name = "capitals_response",
    strict = true,
    structure = capitalsSchema
  )

  service
    .createChatCompletion(
      messages = messages,
      settings = CreateChatCompletionSettings(
        model = ModelId.gpt_5_5,
        max_tokens = Some(1000),
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(jsonSchemaDef)
      )
    )
    .map { response =>
      val json = Json.parse(response.contentHead)
      println(Json.prettyPrint(json))
    }
```

- Create chat completion with **JSON/structured output** using a handly implicit function (`createChatCompletionWithJSON[T]`) that handles JSON extraction with a potential repair, as well as deserialization to an object T.

```scala
  import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

  ...

  service
    .createChatCompletionWithJSON[JsObject](
      messages = messages,
      settings = CreateChatCompletionSettings(
        model = ModelId.gpt_5_5,
        max_tokens = Some(1000),
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(jsonSchemaDef)
      )
    )
    .map { json =>
      println(Json.prettyPrint(json))
    }
```

- **Failover** to alternative models if the primary one fails

```scala
  import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

  val messages = Seq(
    SystemMessage("You are a helpful weather assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  service
    .createChatCompletionWithFailover(
      messages = messages,
      settings = CreateChatCompletionSettings(
        model = ModelId.gpt_5_5
      ),
      failoverModels = Seq(ModelId.gpt_5_4, ModelId.gpt_5_4_mini),
      retryOnAnyError = true,
      failureMessage = "Weather assistant failed to provide a response."
    )
    .map { response =>
      print(response.contentHead)
    }
```

- **Failover** with JSON/structured output

```scala
  import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

  val capitalsSchema = JsonSchema.Object(
    properties = Map(
      "countries" -> JsonSchema.Array(
        items = JsonSchema.Object(
          properties = Map(
            "country" -> JsonSchema.String(
              description = Some("The name of the country")
            ),
            "capital" -> JsonSchema.String(
              description = Some("The capital city of the country")
            )
          ),
          required = Seq("country", "capital")
        )
      )
    ),
    required = Seq("countries")
  )

  val jsonSchemaDef = JsonSchemaDef(
    name = "capitals_response",
    strict = true,
    structure = capitalsSchema
  )

  // Define the chat messages
  val messages = Seq(
    SystemMessage("Give me the most populous capital cities in JSON format."),
    UserMessage("List only african countries")
  )

  // Call the service with failover support
  service
    .createChatCompletionWithJSON[JsObject](
      messages = messages,
      settings = CreateChatCompletionSettings(
        model = ModelId.gpt_5_5, // Primary model
        max_tokens = Some(1000),
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(jsonSchemaDef)
      ),
      failoverModels = Seq(
        ModelId.gpt_5_4,  // First fallback model
        ModelId.gpt_5_4_mini     // Second fallback model
      ),
      maxRetries = Some(3),       // Maximum number of retries per model
      retryOnAnyError = true,     // Retry on any error, not just retryable ones
      taskNameForLogging = Some("capitals-query") // For better logging
    )
    .map { json =>
      println(Json.prettyPrint(json))
    }
```

- **Responses API** - basic usage with textual inputs / messages

```scala
  import io.cequence.openaiscala.domain.responsesapi.Inputs

  service
    .createModelResponse(
      Inputs.Text("What is the capital of France?")
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

```scala
  import io.cequence.openaiscala.domain.responsesapi.Input

  service
    .createModelResponse(
      Inputs.Items(
        Input.ofInputSystemTextMessage(
          "You are a helpful assistant. Be verbose and detailed and don't be afraid to use emojis."
        ),
        Input.ofInputUserTextMessage("What is the capital of France?")
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

- **Responses API** - image input

```scala

  import io.cequence.openaiscala.domain.responsesapi.{Inputs, Input}
  import io.cequence.openaiscala.domain.responsesapi.InputMessageContent
  import io.cequence.openaiscala.domain.ChatRole

  service
    .createModelResponse(
      Inputs.Items(
        Input.ofInputMessage(
          Seq(
            InputMessageContent.Text("what is in this image?"),
            InputMessageContent.Image(
              imageUrl = Some(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg"
              )
            )
          ),
          role = ChatRole.User
        )
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

- **Responses API** - tool use (file search)

```scala

  service
    .createModelResponse(
      Inputs.Text("What are the attributes of an ancient brown dragon?"),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
        tools = Seq(
          FileSearchTool(
            vectorStoreIds = Seq("vs_1234567890"),
            maxNumResults = Some(20),
            filters = None,
            rankingOptions = None
          )
        )
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))

      // citations
      val citations: Seq[Annotation.FileCitation] = response.outputMessageContents.collect {
        case e: OutputText =>
          e.annotations.collect { case citation: Annotation.FileCitation => citation }
      }.flatten

      println("Citations:")
      citations.foreach { citation =>
        println(s"${citation.fileId} - ${citation.filename}")
      }
    }
```

- **Responses API** - tool use (web search)

```scala
  service
    .createModelResponse(
      Inputs.Text("What was a positive news story from today?"),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
        tools = Seq(WebSearchTool())
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))

      // citations
      val citations: Seq[Annotation.UrlCitation] = response.outputMessageContents.collect {
        case e: OutputText =>
          e.annotations.collect { case citation: Annotation.UrlCitation => citation }
      }.flatten

      println("Citations:")
      citations.foreach { citation =>
        println(s"${citation.title} - ${citation.url}")
      }
    }
```

- **Responses API** - tool use (function call)

```scala
  service
    .createModelResponse(
      Inputs.Text("What is the weather like in Boston today?"),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
        tools = Seq(
          FunctionTool(
            name = "get_current_weather",
            parameters = JsonSchema.Object(
              properties = Map(
                "location" -> JsonSchema.String(
                  description = Some("The city and state, e.g. San Francisco, CA")
                ),
                "unit" -> JsonSchema.String(
                  `enum` = Seq("celsius", "fahrenheit")
                )
              ),
              required = Seq("location", "unit")
            ),
            description = Some("Get the current weather in a given location"),
            strict = true
          )
        ),
        toolChoice = Some(ToolChoice.Mode.Auto)
      )
    )
    .map { response =>
      val functionCall = response.outputFunctionCalls.headOption
        .getOrElse(throw new RuntimeException("No function call output found"))

      println(
        s"""Function Call Details:
           |Name: ${functionCall.name}
           |Arguments: ${functionCall.arguments}
           |Call ID: ${functionCall.callId}
           |ID: ${functionCall.id}
           |Status: ${functionCall.status}""".stripMargin
      )

      val toolsUsed = response.tools.map(_.typeString)

      println(s"${toolsUsed.size} tools used: ${toolsUsed.mkString(", ")}")
    }
```

- **Responses API** - tool use (MCP)

```scala
  import io.cequence.openaiscala.domain.responsesapi.tools.Tool
  import io.cequence.openaiscala.domain.responsesapi.tools.mcp.MCPRequireApproval

  service
    .createModelResponse(
      Inputs.Text("Search for information about Scala programming language."),
      settings = CreateModelResponseSettings(
        model = ModelId.gpt_5_4_mini,
        tools = Seq(
          Tool.mcp(
            serverLabel = "deepwiki",
            serverUrl = Some("https://mcp.deepwiki.com/sse"),
            requireApproval = Some(MCPRequireApproval.Setting.Never)
          )
        )
      )
    )
    .map { response =>
      println(response.outputText.getOrElse("N/A"))
    }
```

- **Anthropic** - tool use (requires `openai-scala-anthropic-client` lib). Supports tools such as
  `Tool.bash()`, `Tool.webSearch()`, `Tool.webFetch()`, `Tool.codeExecution()`, `Tool.computer()`,
  `Tool.custom()`, and MCP servers via `MCPServerURLDefinition`.
  See [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/tools).

- **Reasoning effort** - `CreateChatCompletionSettings(reasoning_effort = Some(ReasoningEffort.high))` works across providers:
  OpenAI reasoning models take it natively (`none`/`minimal`/`low`/`medium`/`high`/`xhigh`, plus `max` on the Responses API),
  while the Anthropic, Gemini, and Vertex AI adapters translate it into `output_config.effort` / adaptive thinking
  (Claude) or `thinking_level` / `thinking_budget` (Gemini). The token budgets behind each level are configurable via
  `reasoning-effort-thinking-budget-mapping` in [openai-scala-client.conf](./openai-client/src/main/resources/openai-scala-client.conf).
  Unsupported combinations (e.g. sampling params on GPT-5.x/GPT-6, `minimal` on Gemini 3.7+/Pro) are downgraded automatically
  with a warning by [ChatCompletionSettingsConversions](./openai-core/src/main/scala/io/cequence/openaiscala/service/adapter/ChatCompletionSettingsConversions.scala)
  and the provider adapters.

- **Files and images as provider-uniform attachments** - `FileContent` (PDF) and `ImageURLContent` (JPEG/PNG/GIF/WebP, data URLs or http URLs)
  are accepted by the OpenAI, Anthropic (+ Bedrock), Gemini, and Vertex AI chat completion services. Because only OpenAI
  carries the filename on the wire, [VLMContent](./openai-core/src/main/scala/io/cequence/openaiscala/domain/VLMContent.scala)
  emits each file as a `[file: NAME]` label plus the right content envelope for the file type, so the model can refer
  to files by name on every provider:

```scala
  import io.cequence.openaiscala.domain.VLMContent

  val files: Seq[(String, Array[Byte])] = ... // (fileName, bytes) - PDFs and images

  val messages = Seq(
    SystemMessage(
      "Each attached file is preceded by a label of the form '[file: NAME]'. " +
        "When referring to a file in your answer, use exactly the NAME from its label."
    ),
    UserSeqMessage(
      TextContent("Summarize each attached file in one sentence.") +:
        files.flatMap { case (name, bytes) => VLMContent.of(bytes, name) }
    )
  )

  service.createChatCompletion(
    messages = messages,
    settings = CreateChatCompletionSettings(model = NonOpenAIModelId.claude_sonnet_5)
  )
```
  See the `*WithFileContentAndPdf`, `*WithMultipleNamedPdfsAsFileContent`, and `*VLMSmokeTest` [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples).

- **Anthropic native streaming events** - besides the text-only `createMessageStreamed`, `AnthropicService.createMessageStreamedEvents`
  returns a typed `Source[MessageStreamEvent, NotUsed]` with every SSE event (message start/delta/stop, content block
  start/delta/stop incl. tool_use blocks, ping, usage), which the OpenAI adapter also uses to surface streamed tool calls and to build
  the typed `ChatChunk` stream. Note that Claude Opus 5 / Sonnet 5 / Fable 5.x default to `display = omitted` for thinking - set
  `ThinkingSettings.adaptiveSummarized` (or `thinking.withDisplay(ThinkingDisplay.summarized)`) to receive `thinking_delta` events on the
  native API; the typed stream does this for you. Streamed frames of up to 1 MB are accepted, so large server-tool result blocks
  (web search, web fetch) no longer break the stream.

- **Typed streaming** (🔥 New) - `createChatToolCompletionStreamed` (and the tool-less alias `createChatCompletionStreamedTyped`)
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
  | `Finish(reason, providerReason)` | normalized stop reason (`stop`, `tool_calls`, `length`, `content_filter`, `unknown`) + the provider's own | `finish_reason` | `message_delta.stop_reason` | `finishReason` |
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
  turn thought summaries off. Anthropic's **MCP connector** (🔥 New) rides along the same way: `setAnthropicMcpServers(Seq(MCPServerURLDefinition(name, url)))`
  puts remote MCP servers on the request (typed stream and plain calls alike), their `mcp_tool_use` / `mcp_tool_result` arrive as
  server-side `ToolCall`s / `ToolResult`s, and a `pause_turn` (a tool run that outlived the turn budget) is continued transparently
  on the same `Source` - one `Start`, one final `Finish`, one summed `Usage`; `setAnthropicMaxContinuations` caps it (default 6). See
  [AnthropicCreateChatToolCompletionStreamedWithMCPServers](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/AnthropicCreateChatToolCompletionStreamedWithMCPServers.scala).
  (Anthropic API only - Bedrock rejects `mcp_servers`.)

  **Provider-neutral MCP servers and skills** (🔥 New) - instead of the provider-specific settings above, pass
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
  See [CreateChatToolCompletionStreamedWithMCPServerTool](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamedWithMCPServerTool.scala)
  (one DeepWiki server on OpenAI, Anthropic and Gemini) and
  [AnthropicCreateChatToolCompletionWithSkillTool](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/skills/AnthropicCreateChatToolCompletionWithSkillTool.scala).
  Gemini's native MCP (`setGeminiTools(Seq(Tool.McpServers(...)))`, live-verified 2026-09-16 with Exa, the GitHub Copilot MCP and
  DeepWiki, alone and several per request, authenticated with `x-api-key` or `Authorization: Bearer` transport headers alike): Gemini
  runs the tools itself; Gemini 2.5 streams each call as a server-side `ToolCall` (named `<server>_<tool>`, at times by the bare tool
  name - with MCP servers configured every call that is not one of your declared function tools counts as theirs) plus a `ToolResult`,
  Gemini 3 echoes no call / result parts at all. Its executor fails transiently (HTTP 500 / 503, or a stream that ends right after the call) -
  the adapter surfaces that as a `ToolResult(isError = true)` on the stream and as a `GeminiScalaMcpCallNotExecutedException` (a
  server-error subtype, so `Retryable` through the adapter - the retry adapter re-issues it) on the plain call instead of an empty answer. See
  [GoogleGeminiCreateChatToolCompletionStreamedWithMcpServers](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/googlegemini/GoogleGeminiCreateChatToolCompletionStreamedWithMcpServers.scala).

  GPT-5.6 and GPT-6 Sol / Luna accept function tools on the chat completions API only with `reasoning_effort = none`, and GPT-6
  Astra only on the Responses API - so the full `OpenAIServiceFactory()` / `.withStreaming()` service routes their tool calls and
  typed tool streams through the Responses API automatically, which keeps the requested reasoning (only an explicit
  `reasoning_effort = none` stays on chat completions). A chat-only service forces `none` instead (Astra fails fast). Grok, Groq, Cerebras, Fireworks and DeepSeek use the generic mapping (Groq's per-chunk `usage` is
  emitted once); Sonar and Managed Agents stream text / reasoning / finish / usage but reject tools.

  **Responses API streaming** (🔥 New) - `createModelResponseStreamed(inputs, settings)` on the streamed OpenAI service returns
  `Source[ResponseStreamEvent, NotUsed]` with every server-sent event typed (`ResponseCreated`, `OutputItemAdded/Done`,
  `OutputTextDelta`, `ReasoningSummaryTextDelta`, `FunctionCallArgumentsDelta/Done`, `CodeInterpreterCodeDelta/Done`,
  `ImageGenerationPartialImage`, `OutputTextAnnotationAdded`, `ResponseCompleted/Incomplete/Failed`, `ToolCallStatus` for the
  lifecycle notifications, `UnknownEvent` for the rest), and `createModelResponseStreamedTyped` renders the same stream as `ChatChunk`s -
  reasoning summaries as `Thinking`, the encrypted reasoning as `ThinkingSignature`, web search / code interpreter / MCP / file search /
  image generation as tool-layer chunks plus `WebSearch`, `CodeExecution`, `CodeExecutionResult`, `Image`, annotations as `Citation`.
  A `response.failed` or `error` event fails the stream. See
  [CreateModelResponseStreamed](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/responsesapi/CreateModelResponseStreamed.scala).

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
  [CreateChatToolCompletionStreamedViaResponses](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/responsesapi/CreateChatToolCompletionStreamedViaResponses.scala).
  See [CreateChatToolCompletionStreamed](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamed.scala),
  [AnthropicCreateChatToolCompletionStreamedWithOpenAIAdapter](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/AnthropicCreateChatToolCompletionStreamedWithOpenAIAdapter.scala),
  and [GoogleGeminiCreateChatToolCompletionStreamedWithOpenAIAdapter](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/googlegemini/GoogleGeminiCreateChatToolCompletionStreamedWithOpenAIAdapter.scala).

- **Graders API** - evaluate model outputs

```scala
  import io.cequence.openaiscala.domain.graders._

  val grader = ScoreModelGrader(
    input = Seq(
      GraderModelInput(
        content = GraderInputContent.TextString(
          "Rate the helpfulness of the following response on a scale from 0 to 1:"
        ),
        role = ChatRole.System
      ),
      GraderModelInput(
        content = GraderInputContent.InputText("{{item.question}}"),
        role = ChatRole.User
      ),
      GraderModelInput(
        content = GraderInputContent.OutputText("{{sample.output_json}}"),
        role = ChatRole.Assistant
      )
    ),
    model = ModelId.gpt_5_4_mini,
    name = "helpfulness_scorer",
    range = Seq(0.0, 1.0)
  )

  service
    .runGrader(
      grader = grader,
      modelSample = """{"answer": "The capital of France is Paris."}""",
      item = Map("question" -> "What is the capital of France?")
    )
    .map { result =>
      println(s"Grader evaluation result: $result")
    }
```

- Count expected used tokens before calling `createChatCompletions` or `createChatFunCompletions`, this helps you select proper model and reduce costs. This is an experimental feature and it may not work for all models. Requires `openai-scala-count-tokens` lib.

An example how to count message tokens:
```scala
import io.cequence.openaiscala.service.OpenAICountTokensHelper
import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage, FunctionSpec, ModelId, SystemMessage, UserMessage}

class MyCompletionService extends OpenAICountTokensHelper {
  def exec = {
    val model = ModelId.gpt_5_6_luna

    // messages to be sent to OpenAI
    val messages: Seq[BaseMessage] = Seq(
      SystemMessage("You are a helpful assistant."),
      UserMessage("Who won the world series in 2020?"),
      AssistantMessage("The Los Angeles Dodgers won the World Series in 2020."),
      UserMessage("Where was it played?"),
    )

    val tokenCount = countMessageTokens(model, messages)
  }
}
```

An example how to count message tokens when a function is involved:
```scala
import io.cequence.openaiscala.service.OpenAICountTokensHelper
import io.cequence.openaiscala.domain.{BaseMessage, FunctionSpec, ModelId, SystemMessage, UserMessage}

class MyCompletionService extends OpenAICountTokensHelper {
  def exec = {
    val model = ModelId.gpt_5_6_luna
    
    // messages to be sent to OpenAI
    val messages: Seq[BaseMessage] = 
     Seq(
       SystemMessage("You are a helpful assistant."),
       UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?")
     )
     
    // function to be called
    val function: FunctionSpec = FunctionSpec(
      name = "getWeather",
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "location" -> Map(
            "type" -> "string",
            "description" -> "The city to get the weather for"
          ),
          "unit" -> Map("type" -> "string", "enum" -> List("celsius", "fahrenheit"))
        )
      )
    )

    val tokenCount = countFunMessageTokens(model, messages, Seq(function), Some(function.name))
  }
}
```

**✔️ Important**: After you are done using the service, you should close it by calling `service.close`. Otherwise, the underlying resources/threads won't be released.

---

**III. Using adapters**

Adapters for OpenAI services (chat completion, core, or full) are provided by [OpenAIServiceAdapters](./openai-core/src/main/scala/io/cequence/openaiscala/service/adapter/OpenAIServiceAdapters.scala). The adapters are used to distribute the load between multiple services, retry on transient errors, route, or provide additional functionality. See [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/adapters) for more details.

Note that the adapters can be arbitrarily combined/stacked.

- **Round robin** load distribution 

```scala
  val adapters = OpenAIServiceAdapters.forFullService

  val service1 = OpenAIServiceFactory("your-api-key1")
  val service2 = OpenAIServiceFactory("your-api-key2")

  val service = adapters.roundRobin(service1, service2)
```

- **Random order** load distribution

```scala
  val adapters = OpenAIServiceAdapters.forFullService

  val service1 = OpenAIServiceFactory("your-api-key1")
  val service2 = OpenAIServiceFactory("your-api-key2")

  val service = adapters.randomOrder(service1, service2)
```

- **Logging** function calls

```scala
  val adapters = OpenAIServiceAdapters.forFullService

  val rawService = OpenAIServiceFactory()
  
  val service = adapters.log(
    rawService,
    "openAIService",
    logger.log
  )
```

- **Retry** on transient errors (e.g. rate limit error)

```scala
  val adapters = OpenAIServiceAdapters.forFullService

  implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 10).constantInterval(10.seconds)

  val service = adapters.retry(
    OpenAIServiceFactory(),
    Some(println(_)) // simple logging
  )
```
  `RetrySettings(jitterMs = Some(500))` adds random jitter to the back-off, and `includeExceptionMessage = true` on the `RetryHelpers` methods (`retry` / `retryOnFailure`, below) puts the failing exception's message into the retry log line.
- **Retry** on a specific function using [RetryHelpers](./openai-core/src/main/scala/io/cequence/openaiscala/RetryHelpers.scala) directly
 
```scala
class MyCompletionService @Inject() (
  val actorSystem: ActorSystem,
  implicit val ec: ExecutionContext,
  implicit val scheduler: Scheduler
)(val apiKey: String)
  extends RetryHelpers {
  val service: OpenAIService = OpenAIServiceFactory(apiKey)
  implicit val retrySettings: RetrySettings =
    RetrySettings(interval = 10.seconds)

  def ask(prompt: String): Future[String] =
    for {
      completion <- service
        .createChatCompletion(
          List(UserMessage(prompt))
        )
        .retryOnFailure
    } yield completion.choices.head.message.content
}
```

- **Route** chat completion calls based on models

```scala
  val adapters = OpenAIServiceAdapters.forFullService

  // Anthropic
  val anthropicService = AnthropicServiceFactory.asOpenAI()

  // Groq
  val groqService = OpenAIChatCompletionServiceFactory(ChatProviderSettings.groq)

  // OpenAI
  val openAIService = OpenAIServiceFactory()

  val service: OpenAIService =
    adapters.chatCompletionRouter(
      // OpenAI service is default so no need to specify its models here
      serviceModels = Map(
        groqService -> Seq(NonOpenAIModelId.groq_qwen3_8_27b),
        anthropicService -> Seq(
          NonOpenAIModelId.claude_fable_5_1,
          NonOpenAIModelId.claude_sonnet_5,
          NonOpenAIModelId.claude_haiku_4_5
        )
      ),
      openAIService
    )
```
  `chatCompletionRouterMapped` is the same router with `MappedModel` entries (the model name a caller asks for → the model id actually sent to that provider, e.g. to expose a provider-neutral alias), and `chatCompletionBatchRouterMixed(Mapped)` accepts a mix of batch-capable and plain services.

- **Batch processing** (🔥 New) - provider-agnostic, ~50% of standard cost, async (typically a 24h turnaround target).
  Available on the full OpenAI service and on the Anthropic, Anthropic Bedrock, Gemini, and Vertex AI adapters
  (see the **Batch** column in the provider table above). It is an **opt-in capability**
  ([`OpenAIChatCompletionBatchService`](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIChatCompletionBatchService.scala)),
  deliberately **not** part of the base `OpenAIChatCompletionService`, so a batch caller holds a
  `OpenAIChatCompletionService with OpenAIChatCompletionBatchService` reference - which is exactly what the
  provider factories return. Do **not** down-annotate to plain `OpenAIChatCompletionService` or you lose batch.

  The simplest usage - submit, poll, and retrieve in one call via the
  `createChatCompletionBatchAndWaitForResults` helper (import `OpenAIChatCompletionExtra._`):

```scala
  import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

  // any batch-capable service; here the Anthropic Message Batches adapter
  val service = AnthropicServiceFactory.asOpenAI()

  val requests = Seq(
    ChatCompletionBatchRequest("norway", Seq(UserMessage("Capital of Norway? One word."))),
    ChatCompletionBatchRequest("sweden", Seq(UserMessage("Capital of Sweden? One word.")))
  )

  val results: Future[Seq[ChatCompletionBatchResultItem]] =
    service.createChatCompletionBatchAndWaitForResults(
      requests,
      CreateChatCompletionSettings(NonOpenAIModelId.claude_haiku_4_5),
      pollingInterval = 10.seconds,
      deleteBatchAfterUse = true
    )
```

  For large production batches (thousands of requests, up-to-24h turnaround) prefer the **split flow** - submit,
  persist the returned `(model, batchId)`, and later (a different process/day) poll and retrieve by passing that pair
  back in. The `model` is required alongside the id because a batch id alone is an opaque, provider-specific string,
  not a routing key:

```scala
  val batch    = service.createChatCompletionBatch(requests, settings)              // returns a durable batch id
  // ... persist (settings.model, batch.id), rebuild the service later ...
  val info     = service.getChatCompletionBatch(batchId, model)                     // poll until info.isDone
  val results  = service.retrieveChatCompletionBatchResults(batchId, model)         // match items by customId
  service.deleteChatCompletionBatch(batchId, model)                                 // clean up staged files
```

  **Typed JSON batches** - `createChatCompletionBatchWithJSON[T]` is the batch twin of `createChatCompletionWithJSON`: it
  applies the JSON schema (or the prompt-appendix fallback for models without json-schema support) to every request,
  waits for the batch, and deserializes each result to `T`. Per-item problems come back as `Left(ChatCompletionBatchError)`
  (never failing the whole future), `failoverModels` resubmits the whole batch on a terminal batch-level failure, and a
  `timeout` raises a typed `OpenAIScalaBatchTimeoutException` carrying the batch id so it can be picked up later:

```scala
  val results: Future[Seq[ChatCompletionBatchTypedResultItem[Capitals]]] =
    service.createChatCompletionBatchWithJSON[Capitals](
      requests,
      CreateChatCompletionSettings(
        model = NonOpenAIModelId.claude_sonnet_5,
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(jsonSchemaDef)
      ),
      pollingInterval = 30.seconds,
      timeout = Some(2.hours),
      failoverModels = Seq(NonOpenAIModelId.claude_haiku_4_5)
    )
```

  On Anthropic Bedrock, batch goes through Bedrock's own S3-staged batch inference and needs
  `AnthropicServiceFactory.bedrockAsOpenAIWithBatchSupport(s3Bucket, roleArn)` (the plain `bedrockAsOpenAI()` is not batch-capable).

- **Batch router** (🔥 New) - the batch-aware sibling of `chatCompletionRouter`, routing the batch endpoints across
  providers by model. Every registered service (and the default) must be batch-capable, and it respects the adapter's
  service type: `forFullService.chatCompletionBatchRouter(...)` returns an `OpenAIService` whose chat completion and
  batch are routed by model while files/assistants/etc. still delegate to the default service. Ideal for a central
  batch registry - submit through the router, persist `(model, batchId)`, and rebuild the identical router later to
  poll. See [ChatCompletionBatchRegistryPollingDemo](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/ChatCompletionBatchRegistryPollingDemo.scala).

```scala
  val geminiService = GeminiServiceFactory.asOpenAI()       // batch-capable
  val anthropicService = AnthropicServiceFactory.asOpenAI() // batch-capable (default)

  val router = OpenAIServiceAdapters.forChatCompletionService.chatCompletionBatchRouter(
    serviceModels = Map(geminiService -> Seq(NonOpenAIModelId.gemini_3_8_flash)),
    anthropicService
  )

  // routed by settings.model on submit, and by the explicit `model` arg on status/results/cancel/delete
  val batch   = router.createChatCompletionBatch(requests, CreateChatCompletionSettings(NonOpenAIModelId.gemini_3_8_flash))
  val results = router.retrieveChatCompletionBatchResults(batch.id, NonOpenAIModelId.gemini_3_8_flash)
```

  To register a provider that has **no native batch support** in a batch router, wrap it with
  `chatCompletionBatchEmulated` - a fallback adapter that satisfies the batch interface by running the requests as
  ordinary synchronous chat completions, logging a warning that native batch is unavailable (no batch discount, no
  async processing, results held in memory). This lets a single router mix natively-batching providers with fallback
  ones:

```scala
  // Perplexity Sonar has no batch API - emulate it so it can join the router as a fallback
  val sonarService = SonarServiceFactory.asOpenAI()
  val sonarBatch   = OpenAIServiceAdapters.chatCompletionBatchEmulated(sonarService) // warns + runs sync on batch calls

  val router = OpenAIServiceAdapters.forChatCompletionService.chatCompletionBatchRouter(
    serviceModels = Map(
      geminiService -> Seq(NonOpenAIModelId.gemini_3_8_flash), // native batch
      sonarBatch    -> Seq(NonOpenAIModelId.sonar)             // emulated fallback
    ),
    anthropicService
  )
```

- **Chat-to-completion** adapter

```scala
    val adapters = OpenAIServiceAdapters.forCoreService

    val service = adapters.chatToCompletion(
      OpenAICoreServiceFactory(
        coreUrl = "https://api.fireworks.ai/inference/v1/",
        authHeaders = Seq(("Authorization", s"Bearer ${sys.env("FIREWORKS_API_KEY")}"))
      )
    )
```

- **Intercept** success and error calls (stacked adapters)

```scala
  val adapters = OpenAIServiceAdapters.forFullService

  val service = adapters.chatCompletionIntercept(data =>
    Future {
      println(
        s"Chat completion succeeded in ${data.execTimeMs} ms " +
          s"(model: ${data.settings.model}, " +
          s"messages: ${data.messages.size}, " +
          s"response tokens: ${data.response.usage.map(_.completion_tokens).getOrElse("N/A")})"
      )
    }
  )(
    adapters.chatCompletionErrorIntercept(data =>
      Future {
        println(
          s"Chat completion FAILED after ${data.execTimeMs} ms " +
            s"(model: ${data.settings.model}, " +
            s"messages: ${data.messages.size}, " +
            s"error: ${data.error.getMessage})"
        )
      }
    )(
      OpenAIServiceFactory()
    )
  )
```

- **Input/output transformation** - `chatCompletionInput()` and `chatCompletionOutput()` adapters
  for transforming messages/settings on input or assistant messages on output.
  See [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/adapters).

**IV. Sharing an HTTP engine**

Every plain factory call (`OpenAIServiceFactory()`, `AnthropicServiceFactory()`, ...) spins up
its own HTTP client pool **and** its own dedicated actor system (created eagerly, all daemon
threads, so a leaked service can't block JVM exit) - fine for a handful of long-lived services,
wasteful if you're building many services, or many providers, in the same app. Since the
`ws-client` 1.0 engine-discovery migration you can build **one engine** and share it across
**any number of services, including across different providers**:

```scala
import io.cequence.wsclient.service.spi.StreamedEngineRegistry

implicit val ec: ExecutionContext = ExecutionContext.global

val engine = StreamedEngineRegistry.outputStreamed() // one pool + one (daemon) actor system

val openAI = OpenAIServiceFactory.withEngine(engine)       // api key from config/env
val anthropic = AnthropicServiceFactory.withEngine(engine) // api key from env
val gemini = GeminiServiceFactory.withEngine(engine)       // api key from env

// ... use the services ...

anthropic.close() // closes a service on a SHARED engine without touching the engine itself -
                   // openAI and gemini keep working
engine.close()     // the one real teardown - close it once, after every service using it is done
```

**Timeouts (and proxy) are engine-level**, baked into the HTTP client at construction time and
deliberately not overridable per call. All four `Timeouts` fields are in **milliseconds** (the
`*Sec`-suffixed keys in the config file, e.g. `requestTimeoutSec`, are the seconds-based
equivalent - see the Config section above). A service that needs different timeouts than the rest
of a shared setup gets its own **engine copy**, which shares the parent's actor system (so you
don't pay for a second one) but builds its own HTTP client:

```scala
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.ws.Timeouts

val engine = StreamedEngineRegistry.outputStreamed() // default timeouts

// e.g. a batch/VLM provider that legitimately needs much longer timeouts than the rest
val slowEngine = engine.copy(
  TransportSettings(timeouts = Timeouts(
    requestTimeout = Some(300000),   // 300s
    readTimeout = Some(300000),      // 300s
    connectTimeout = Some(20000),    // 20s
    pooledConnectionIdleTimeout = Some(60000) // 60s
  ))
)

val fastService = OpenAIServiceFactory.withEngine(engine)
val slowService = AnthropicServiceFactory.withEngine(slowEngine)

slowService.close() // only slowEngine's own HTTP client - the shared actor system lives on
fastService.close()
engine.close()       // tear down the shared actor system last
```

You can also set timeouts on a single, non-shared service directly, without touching engines:

```scala
val service = OpenAIServiceFactory(
  apiKey = "your_api_key",
  timeouts = Some(Timeouts(requestTimeout = Some(120000), readTimeout = Some(120000))) // 120s
)
```

To embed a service into an existing Akka application (one Akka app, one `ActorSystem`), build
the engine on YOUR `Materializer` instead of letting it create its own, so closing the service
never touches your actor system:

```scala
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine

implicit val system: ActorSystem = /* your app's existing ActorSystem */ ???
implicit val materializer: Materializer = Materializer(system)
implicit val ec: ExecutionContext = system.dispatcher

val engine = new PlayWSStreamClientEngine() // runs on YOUR materializer/ec
val service = OpenAIServiceFactory.withEngine(engine)

service.close() // closes only the HTTP client - your ActorSystem is untouched
```

> **Akka backend, for now.** This library's streaming API currently returns
> `Source[T, akka.NotUsed]` and depends on the Akka-flavored `ws-client` engines. `ws-client`
> itself is no longer Akka-only - it also ships Pekko engines, backend-only engines with no
> actor system (JDK, sttp), and a family-neutral streaming core underneath all of them. A live
> experiment already swapped the Akka dependency for the Pekko one and ran **synchronous** calls
> with zero source changes; **streaming** is still Akka-specific in this repo (a few akka types
> baked into the public API) and will likely be abstracted away in a future release so you can
> pick your own backend. Nothing you need to do today - just don't be surprised if the streaming
> API becomes backend-agnostic later.

## TypeSafe AI (Jev) 🎯

`openai-scala-typesafe-client` wraps TypeSafe's **System One** API (`POST /v1/systemone`, model `jev-latest`). It is not a
chat model: you send a `state` (text or JSON) plus named, typed questions and get typed answers with calibrated
probabilities back in ~100 ms - so there is no streaming (the API ignores a `stream` flag rather than honouring it), and
the `asOpenAI()` adapter serves structured output only. Three question kinds: `ChoiceQuestion` (one of a fixed set),
`ScoreQuestion` (a level on an ordered rubric) and `NoulQuestion` (yes/no). Ask everything at once - the questions are
evaluated independently in one call. Requires `TYPESAFE_API_KEY` (optional `TYPESAFE_BASE_URL`, `TYPESAFE_DEFAULT_MODEL`).

```scala
  import io.cequence.openaiscala.typesafe.domain._
  import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory

  val typeSafe = TypeSafeServiceFactory()  // TYPESAFE_API_KEY (+ optional TYPESAFE_BASE_URL, TYPESAFE_DEFAULT_MODEL)

  typeSafe.systemOne(
    state = "I've been trying to connect my Stripe account for 3 days. I'm losing sales. Please help ASAP.",
    questions = Map(
      "department"  -> ChoiceQuestion("Which team should handle this", "billing" -> "Payments", "technical" -> "Bugs, integrations", "sales" -> "Pricing"),
      "frustration" -> ScoreQuestion("How frustrated is the customer?", "Calm", "Frustrated but civil", "Very angry"),
      "is_urgent"   -> NoulQuestion("The message conveys urgency")
    )
  ).map { response =>
    val department = response.choice("department")   // .choice = "technical", .confidence, .probabilities
    val frustration = response.score("frustration")  // .score = 1.04 (between levels 1 and 2), .legend, .probabilities
    val urgent = response.noul("is_urgent")          // .noul = 0.999
    if (department.confidence < 0.7) escalateToHuman() else routeTo(department.choice)
  }
```

Every response carries token usage (`usage.input_tokens` is what you are billed for, `output_tokens` is currently free and
grows with the number of questions). Model names come from `typeSafe.listModels`; `TypeSafeServiceFactory.withEngine(engine)`
shares one engine with the other providers. The wire format is pinned against TypeSafe's published OpenAPI spec and the
official Python SDK's fixtures in the module's tests, and `examples/typesafe/TypeSafeSmokeTest` runs every question shape,
JSON states, parallel calls, the retry adapter and the error mapping against the live API (`jev-latest` resolves to a dated
build such as `jev-1.13.0`, named in the response; `jev-preview` is the next one).

**Errors** are `TypeSafeScalaClientException`s classified by status and body - `TypeSafeScalaUnauthorizedException`
(401/403), `TypeSafeScalaTokenCountExceededException` (the ~32k-token input limit), `TypeSafeScalaApiUsageException`
(unknown model, invalid JSON, feature not enabled), `TypeSafeScalaInvalidRequestException` (422 validation, with
`violations`), `TypeSafeScalaRateLimitException` (429), `TypeSafeScalaEngineOverloadedException` (529/503), ... - each
carrying the HTTP code, the API's `error_type` and the `x-typesafe-request-id`. `TypeSafeRetryable` picks the ones worth
retrying and `TypeSafeServiceAdapters.retry(typeSafe)` backs off exactly there. Through `asOpenAI()` they surface as the
usual `OpenAIScala*` exceptions with the native one as the cause.

**Through the OpenAI interface.** `TypeSafeServiceFactory.asOpenAI()` is an `OpenAIChatCompletionService` for
STRUCTURED OUTPUT: the request must set `response_format_type = json_schema` with a closed-vocabulary `jsonSchema` -
booleans (noul), string enums (choice), numeric enums or small `minimum`..`maximum` ranges (score; `JsonSchema.Integer` /
`Number` carry these as optional fields - `enum` is portable, while `minimum` / `maximum` are honoured by OpenAI in strict
mode and stripped by the Anthropic adapter, which would otherwise get a 400), arrays of string enums (multi-select nouls)
and nested objects of those. The schema becomes the questions and the messages the state (system messages as
`instructions`, a user message that is a JSON object embedded as JSON, several turns as a `conversation`), and the
assistant message's content is a JSON document of the schema - so `createChatCompletionWithJSON[T]` (and the routers,
retry, logging and interception adapters) work unchanged, and the calibrated probabilities ride in `originalResponse` as
the `SystemOneResponse`.

```scala
  import io.cequence.openaiscala.domain._
  import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
  import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
  import io.cequence.openaiscala.typesafe.domain.{SystemOneResponse, TypeSafeModelId}
  import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory

  val service = TypeSafeServiceFactory.asOpenAI()   // an OpenAIChatCompletionService; TYPESAFE_API_KEY
  // or over an existing service, e.g. one on a shared engine or wrapped in the retry adapter:
  // val service = TypeSafeServiceFactory.asOpenAI(TypeSafeServiceAdapters.retry(TypeSafeServiceFactory.withEngine(engine)))

  case class Triage(department: String, is_urgent: Boolean, frustration: Int, topics: Seq[String])
  implicit val triageFormat: Format[Triage] = Json.format[Triage]

  // closed vocabulary only: enum -> choice, boolean -> noul, bounded integer -> score, array of enum -> multi-select
  val triageSchema = JsonSchemaDef("triage", strict = true, structure = Left(JsonSchema.Object(
    properties = Seq(
      "department"  -> JsonSchema.String(Some("Which team should handle this"), `enum` = Seq("billing", "technical", "sales")),
      "is_urgent"   -> JsonSchema.Boolean(Some("The message conveys time pressure")),
      "frustration" -> JsonSchema.Integer(Some("How frustrated the customer appears, 1 calm .. 5 furious"), minimum = Some(1), maximum = Some(5)),
      "topics"      -> JsonSchema.Array(JsonSchema.String(`enum` = Seq("payments", "integration", "pricing")), Some("What the message is about"))
    ),
    required = Seq("department", "is_urgent", "frustration", "topics")
  )))

  service.createChatCompletionWithJSONFullResponse[Triage](
    Seq(
      SystemMessage("You triage support tickets for a payments platform."),   // -> state.instructions
      UserMessage("My Stripe connection has been failing for 3 days. I'm losing sales, please help ASAP.")  // -> state.message
    ),
    CreateChatCompletionSettings(model = TypeSafeModelId.jev_latest).withJsonSchema(triageSchema)
  ).map { case (triage, response) =>
    triage                                                        // Triage(technical, true, 4, List(payments, integration))
    response.originalResponse.collect { case r: SystemOneResponse =>
      r.choice("department").ranked                               // List((technical, 0.98), (billing, 0.02), (sales, 0.0))
    }
  }
```

`TypeSafeChatMapping.toState(messages)` / `toQuestions(schema)` show what a call will send, and
`examples/typesafe/TypeSafeOpenAIAdapterWalkthrough` prints the exact System One request an OpenAI-shaped call turns into.
Of the standard settings only `model`, `response_format_type`, `jsonSchema` and `n` = 1 are honoured; anything else you
set (temperature, `max_tokens`, `seed`, `reasoning_effort`, ...) is dropped with one warning naming it, since System One
does not sample. A free-form string in the schema, a plain (non-`json_schema`) request, `n > 1`, tools, streaming and
image content are refused up front with an explanation. The `jev-*` ids are listed under `models-supporting-json-schema`,
so the JSON helper keeps the request in schema mode, and a `chatCompletionRouter` can send closed-vocabulary schemas to
Jev and everything else to an LLM. See `examples/typesafe/TypeSafeCreateChatCompletionWithJSON`, and
`TypeSafeOpenAIAdapterScenarios` for fifteen "how to call it and what happens when" cases (JSON user messages, system
instructions, multi-turn, thresholds, `originalResponse`, dropped settings, and every refusal).
`TypeSafeSemanticFind` ports the [semantic search cookbook](https://docs.typesafe.ai/cookbooks/semantic_find) to the
adapter: the 218 tagged lines of GitHub's Terms of Service are the state, a string enum over the line ids ranks every
line by relevance and a boolean tells whether the document answers the query at all, in one ~200 ms request.

## Anthropic Managed Agents 🤝

The `openai-scala-anthropic-client` module covers Anthropic's **Managed Agents** REST API (beta `managed-agents-2026-04-01`,
not available on Bedrock) as part of the native `AnthropicService`: agents (`createAgent`, `listAgents`, `getAgent`,
`updateAgent`, `archiveAgent`, `listAgentVersions`), environments and their work queue (`createEnvironment`, `pollWork`,
`acknowledgeWork`, `recordWorkHeartbeat`, `stopWork`, `getWorkQueueStats`, ...), sessions with events, resources and
threads (`createSession`, `sendSessionEvents`, `streamSessionEvents`, `addSessionResource`, ...), deployments and runs
(`createDeployment`, `runDeployment`, `pauseDeployment`, `listDeploymentRuns`, ...), vaults and credentials
(`createVault`, `createCredential`, `mcpOAuthValidateCredential`, ...), and memory stores with versioned memories
(`createMemoryStore`, `createMemory`, `listMemoryVersions`, `redactMemoryVersion`, ...).

```scala
  import io.cequence.openaiscala.anthropic.domain.managedagents._
  import io.cequence.openaiscala.anthropic.domain.settings.{AnthropicCreateAgentSettings}

  val service: AnthropicService = AnthropicServiceFactory() // ANTHROPIC_API_KEY, or forAuthToken() / forOAuthProfile()

  for {
    agent <- service.createAgent(
      AnthropicCreateAgentSettings(
        name = "docs assistant",
        model = AgentModelConfig(NonOpenAIModelId.claude_opus_5),
        system = Some("You are a concise assistant."),
        tools = Seq(AgentTool.Toolset())
      )
    )
    versions <- service.listAgentVersions(agent.id)
    _ <- service.archiveAgent(agent.id)
  } yield versions
```

To drive a managed agent through the regular OpenAI chat-completion interface (routers, retries, streaming, ...) use
the adapter - each `createChatCompletion` call runs one session turn, agents/environments are created lazily and cached
when not given explicitly:

```scala
  val service = AnthropicServiceFactory.managedAgentAsOpenAI(
    agentId = None,        // or Some("agent_...") to pin a pre-created agent (its model wins over settings.model)
    environmentId = None   // or Some("env_...")
  )
```

See the [managedagents examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/managedagents)
for end-to-end flows over every resource type.

## Claude Agent Client 🖥️

`claude-agent-client` is a separate module that wraps the **`claude` CLI as a subprocess**
(NDJSON over stdin/stdout), giving a typed, bidirectional session API compatible with the
Claude Agent SDK protocol - including tool-permission callbacks and mid-turn interrupt. This
is a fundamentally different transport from the rest of this library: it does **not** provide
an `asOpenAI()` adapter and is not a drop-in `OpenAIChatCompletionService`. It's distinct from
the HTTP-based `AnthropicManagedAgentService` (part of `anthropic-client`), which talks
directly to Anthropic's Managed Agents REST API instead of spawning a local process.

Add the dependency:

```
"io.cequence" %% "openai-scala-claude-agent-client" % "1.3.0"
```

```scala
  import io.cequence.openaiscala.claudeagent.domain.ClaudeAgentSettings
  import io.cequence.openaiscala.claudeagent.service.ClaudeAgentServiceFactory

  val service = ClaudeAgentServiceFactory.startSession(
    ClaudeAgentSettings(model = Some("claude-haiku-4-5"))
  )

  val observed = service.events.runForeach(event => println(event)) // subscribe first
  service.ready.flatMap { _ =>
    service.send("Explain the difference between Scala's Option and Try in one sentence.")
  }
```

`ready` completes after the CLI's `system/init` handshake; `send` and control requests wait for
it automatically. `completion` exposes the eventual process exit code and a bounded stderr tail.
The event stream is hot after its initial init replay, so subscribe before sending a turn whose
events must be observed. To approve a tool call unchanged, reply with
`PermissionDecision.Allow(request.input)`; the CLI requires an explicit `updated_input` object.

See [ClaudeAgentOneShotQueryExample](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/claudeagent/ClaudeAgentOneShotQueryExample.scala)
for a complete runnable example, and
[ClaudeAgentToolPermissionExample](./openai-examples/src/main/scala/io/cequence/openaiscala/examples/claudeagent/ClaudeAgentToolPermissionExample.scala)
for a full bidirectional session that handles tool-permission requests.

**Requires the `claude` CLI installed separately** (e.g. `npm install -g
@anthropic-ai/claude-code`) and authenticated - either via an interactive Claude subscription
login (`claude /login`) or one of `ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN` /
`CLAUDE_CODE_OAUTH_TOKEN` in the environment.

## FAQ 🤔

1. _Wen Scala 3?_ 

   ~~Feb 2023. You are right; we chose the shortest month to do so :)~~
 **Done!**


2. _I got a timeout exception. How can I change the timeout setting?_

   You can do it either by passing the `timeouts` param to `OpenAIServiceFactory` or, if you use your own configuration file, then you can simply add it there as: 

```
openai-scala-client {
    timeouts {
        requestTimeoutSec = 200
        readTimeoutSec = 200
        connectTimeoutSec = 5
        pooledConnectionIdleTimeoutSec = 60
    }
}
```

3. _I got an exception like `com.typesafe.config.ConfigException$UnresolvedSubstitution: openai-scala-client.conf @ jar:file:.../io/cequence/openai-scala-client_2.13/0.0.1/openai-scala-client_2.13-0.0.1.jar!/openai-scala-client.conf: 4: Could not resolve substitution to a value: ${OPENAI_SCALA_CLIENT_API_KEY}`. What should I do?_

   Set the env. variable `OPENAI_SCALA_CLIENT_API_KEY`. If you don't have one register [here](https://beta.openai.com/signup).


4. _It all looks cool. I want to chat with you about your research and development?_

   Just shoot us an email at [openai-scala-client@cequence.io](mailto:openai-scala-client@cequence.io?subject=Research%20andDevelopment).

## License ⚖️

This library is available and published as open source under the terms of the [MIT License](https://opensource.org/licenses/MIT).

## Contributors 🙏

This project is open-source and welcomes any contribution or feedback ([here](https://github.com/cequence-io/openai-scala-client/issues)).

Development of this library has been supported by  [<img src="https://cequence.io/favicon-16x16.png"> - Cequence.io](https://cequence.io) - `The future of contracting` 

Created and maintained by [Peter Banda](https://peterbanda.net) (on X [here](https://x.com/0xbnd)).
