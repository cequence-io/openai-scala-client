# OpenAI Scala Client 🤖
[![version](https://img.shields.io/badge/version-1.3.0.RC.3-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT) ![GitHub Stars](https://img.shields.io/github/stars/cequence-io/openai-scala-client?style=social) [![Twitter Follow](https://img.shields.io/twitter/follow/0xbnd?style=social)](https://twitter.com/0xbnd) ![GitHub CI](https://github.com/cequence-io/openai-scala-client/actions/workflows/continuous-integration.yml/badge.svg)

This is a no-nonsense async Scala client for OpenAI API and multiple LLM providers supporting all the available endpoints and params **including streaming**, **chat completion**, **responses API**, **assistants API**, **tools** (including MCP), **graders**, **vision**, and **voice routines** (as defined [here](https://platform.openai.com/docs/api-reference)), provided in a single, convenient service called [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala) with adapters for Anthropic, Google Gemini/Vertex AI, Groq, Perplexity, and others. The supported calls are:

* **Models**: [listModels](https://platform.openai.com/docs/api-reference/models/list), and [retrieveModel](https://platform.openai.com/docs/api-reference/models/retrieve)
* **Completions**: [createCompletion](https://platform.openai.com/docs/api-reference/completions/create)
* **Chat Completions**: [createChatCompletion](https://platform.openai.com/docs/api-reference/chat/create), [createChatFunCompletion](https://platform.openai.com/docs/api-reference/chat/create) (deprecated), and [createChatToolCompletion](https://platform.openai.com/docs/api-reference/chat/create)
* **Edits**: [createEdit](https://platform.openai.com/docs/api-reference/edits/create) (deprecated)
* **Images**: [createImage](https://platform.openai.com/docs/api-reference/images/create), [createImageEdit](https://platform.openai.com/docs/api-reference/images/create-edit), and [createImageVariation](https://platform.openai.com/docs/api-reference/images/create-variation)
* **Embeddings**: [createEmbeddings](https://platform.openai.com/docs/api-reference/embeddings/create)
* **Batches**: [createBatch](https://platform.openai.com/docs/api-reference/batch/create), [retrieveBatch](https://platform.openai.com/docs/api-reference/batch/retrieve), [cancelBatch](https://platform.openai.com/docs/api-reference/batch/cancel), and [listBatches](https://platform.openai.com/docs/api-reference/batch/list)
* **Audio**: [createAudioTranscription](https://platform.openai.com/docs/api-reference/audio/createTranscription), [createAudioTranslation](https://platform.openai.com/docs/api-reference/audio/createTranslation), and [createAudioSpeech](https://platform.openai.com/docs/api-reference/audio/createSpeech)
* **Files**: [listFiles](https://platform.openai.com/docs/api-reference/files/list), [uploadFile](https://platform.openai.com/docs/api-reference/files/upload), [deleteFile](https://platform.openai.com/docs/api-reference/files/delete), [retrieveFile](https://platform.openai.com/docs/api-reference/files/retrieve), and [retrieveFileContent](https://platform.openai.com/docs/api-reference/files/retrieve-content)
* **Fine-tunes**: [createFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/create), [listFineTunes](https://platform.openai.com/docs/api-reference/fine-tunes/list), [retrieveFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/retrieve), [cancelFineTune](https://platform.openai.com/docs/api-reference/fine-tunes/cancel), [listFineTuneEvents](https://platform.openai.com/docs/api-reference/fine-tunes/events), [listFineTuneCheckpoints](https://platform.openai.com/docs/api-reference/fine-tuning/list-checkpoints), and [deleteFineTuneModel](https://platform.openai.com/docs/api-reference/fine-tunes/delete-model)
* **Moderations**: [createModeration](https://platform.openai.com/docs/api-reference/moderations/create)
* **Assistants**: [createAssistant](https://platform.openai.com/docs/api-reference/messages/createMessage), [listAssistants](https://platform.openai.com/docs/api-reference/assistants/listAssistants), [retrieveAssistant](https://platform.openai.com/docs/api-reference/assistants/retrieveAssistant), [modifyAssistant](https://platform.openai.com/docs/api-reference/assistants/modifyAssistant), and [deleteAssistant](https://platform.openai.com/docs/api-reference/assistants/deleteAssistant)
* **Threads**: [createThread](https://platform.openai.com/docs/api-reference/threads/createThread), [retrieveThread](https://platform.openai.com/docs/api-reference/threads/getThread), [modifyThread](https://platform.openai.com/docs/api-reference/threads/modifyThread), and [deleteThread](https://platform.openai.com/docs/api-reference/threads/deleteThread)
* **Thread Messages**: [createThreadMessage](https://platform.openai.com/docs/api-reference/assistants/createAssistant), [retrieveThreadMessage](https://platform.openai.com/docs/api-reference/messages/getMessage), [modifyThreadMessage](https://platform.openai.com/docs/api-reference/messages/modifyMessage), [listThreadMessages](https://platform.openai.com/docs/api-reference/messages/listMessages), [retrieveThreadMessageFile](https://platform.openai.com/docs/api-reference/messages/getMessageFile), and [listThreadMessageFiles](https://platform.openai.com/docs/api-reference/messages/listMessageFiles)
* **Runs**: [createRun](https://platform.openai.com/docs/api-reference/runs/createRun), [createThreadAndRun](https://platform.openai.com/docs/api-reference/runs/createThreadAndRun), [listRuns](https://platform.openai.com/docs/api-reference/runs/listRuns), [retrieveRun](https://platform.openai.com/docs/api-reference/runs/retrieveRun), [modifyRun](https://platform.openai.com/docs/api-reference/runs/modifyRun), [submitToolOutputs](https://platform.openai.com/docs/api-reference/runs/submitToolOutputs), and [cancelRun](https://platform.openai.com/docs/api-reference/runs/cancelRun)
* **Run Steps**: [listRunSteps](https://platform.openai.com/docs/api-reference/run-steps/listRunSteps), and [retrieveRunStep](https://platform.openai.com/docs/api-reference/run-steps/getRunStep) 
* **Vector Stores**: [createVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/create), [listVectorStores](https://platform.openai.com/docs/api-reference/vector-stores/list), [retrieveVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/retrieve), [modifyVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/modify), and [deleteVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/delete)
* **Vector Store Files**: [createVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/createFile), [listVectorStoreFiles](https://platform.openai.com/docs/api-reference/vector-stores-files/listFiles), [retrieveVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/getFile), and [deleteVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/deleteFile)  
* **Vector Store File Batches**: [createVectorStoreFileBatch](https://platform.openai.com/docs/api-reference/vector-stores-file-batches/createBatch), [retrieveVectorStoreFileBatch](https://platform.openai.com/docs/api-reference/vector-stores-file-batches/getBatch), [cancelVectorStoreFileBatch](https://platform.openai.com/docs/api-reference/vector-stores-file-batches/cancelBatch), and [listVectorStoreBatchFiles](https://platform.openai.com/docs/api-reference/vector-stores-file-batches/listBatchFiles)
* **Responses**: [createModelResponse](https://platform.openai.com/docs/api-reference/responses/create) (🔥 with tools support), [getModelResponse](https://platform.openai.com/docs/api-reference/responses/get), [deleteModelResponse](https://platform.openai.com/docs/api-reference/responses/delete), [cancelModelResponse](https://platform.openai.com/docs/api-reference/responses/cancel), [getModelResponseInputTokenCounts](https://platform.openai.com/docs/api-reference/responses/token-counts), and [listModelResponseInputItems](https://platform.openai.com/docs/api-reference/responses/input-items)
* **Graders** (🔥 new): [runGrader](https://platform.openai.com/docs/api-reference/graders/run), and [validateGrader](https://platform.openai.com/docs/api-reference/graders/validate)

Note that in order to be consistent with the OpenAI API naming, the service function names match exactly the API endpoint titles/descriptions in camelCase.
Also, we aimed for the library to be self-contained with the fewest dependencies possible. Therefore, we implemented our own generic WS client (currently with Play WS backend, which can be swapped for other engines in the future). Additionally, if dependency injection is required, we use the `scala-guice` library.

---

👉 **No time to read a lengthy tutorial? Sure, we hear you! Check out the [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples) to see how to use the lib in practice.**

---

In addition to OpenAI, this library supports many other LLM providers. For providers that aren't natively compatible with the chat completion API, we've implemented adapters to streamline integration (see [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples)).

| Provider | JSON/Structured Output | Tools Support                     | Batch (🔥 New)          | Description |
|----------|------------------------|-----------------------------------|-------------------------|-------------|
| [OpenAI](https://platform.openai.com) | Full                   | Standard + Responses API          | Yes                     | Full API support |
| [Azure OpenAI](https://azure.microsoft.com/en-us/products/ai-services/openai-service) | Full                   | Standard + Responses API          | Yes                     | OpenAI on Azure|
| [Anthropic](https://www.anthropic.com/api) | Full (🔥 New)          | Yes, also MCP and Skills (🔥 New) | Yes                     | Claude models |
| [Anthropic Bedrock](https://aws.amazon.com/bedrock/claude/) | Full (🔥 New)          | Yes, also MCP (🔥 New)            | Yes (no prompt caching) | Claude on AWS |
| [OpenAI Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html) | Full (🔥 New)          | Standard + Responses API          |                         | GPT-5.x & gpt-oss on AWS (`bedrock-mantle`) |
| [Azure AI](https://azure.microsoft.com/en-us/products/ai-studio) | Varies                 |                                   |                         | Open-source models |
| [Cerebras](https://cerebras.ai/) | Only JSON object mode  |                                   |                         | Fast inference |
| [Deepseek](https://deepseek.com/) | Only JSON object mode  |                                   |                         | Chinese provider |
| [FastChat](https://github.com/lm-sys/FastChat) | Varies                 |                                   |                         | Local LLMs |
| [Fireworks AI](https://fireworks.ai/) | Only JSON object mode  |                                   |                         | Cloud provider |
| [Google Gemini](https://ai.google.dev/) | Full                   | Yes (🔥 New)                      | Yes                     | Google's models |
| [Google Vertex AI](https://cloud.google.com/vertex-ai) | Full                   | Yes                               | Yes                     | Gemini models |
| [Grok](https://x.ai/) | Full                   |                                   |                         | x.AI models |
| [Groq](https://wow.groq.com/) | Only JSON object mode  |                                   | Yes                     | Fast inference |
| [Mistral](https://mistral.ai/) | Only JSON object mode  |                                   |                         | Open-source leader |
| [Novita](https://novita.ai/) | Only JSON object mode  |                                   |                         | Cloud provider |
| [Octo AI](https://octo.ai/) | Only JSON object mode  |                                   |                         | Cloud provider (obsolete) |
| [Ollama](https://ollama.com/) | Varies                 |                                   |                         | Local LLMs |
| [Perplexity Sonar](https://www.perplexity.ai/) | Only implied           |                                   |                         | Search-based AI |
| [TogetherAI](https://www.together.ai/) | Only JSON object mode  |                                   |                         | Cloud provider |

---

👉 For background information how the project started read an article about the lib/client on [Medium](https://medium.com/@0xbnd/openai-scala-client-is-out-d7577de934ad).

Also try out our [Scala client for Pinecone vector database](https://github.com/cequence-io/pinecone-scala), or use both clients together! [This demo project](https://github.com/cequence-io/pinecone-openai-scala-demo) shows how to generate and store OpenAI embeddings into Pinecone and query them afterward. The OpenAI + Pinecone combo is commonly used for autonomous AI agents, such as [babyAGI](https://github.com/yoheinakajima/babyagi) and [AutoGPT](https://github.com/Significant-Gravitas/Auto-GPT).

**✔️ Important**: this is a "community-maintained" library and, as such, has no relation to OpenAI company.

## Installation 🚀

The currently supported Scala versions are **2.12, 2.13**, and **3**.  

To install the library, add the following dependency to your *build.sbt*

```
"io.cequence" %% "openai-scala-client" % "1.3.0.RC.3"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>io.cequence</groupId>
    <artifactId>openai-scala-client_2.12</artifactId>
    <version>1.3.0.RC.3</version>
</dependency>
```

If you want streaming support, use `"io.cequence" %% "openai-scala-client-stream" % "1.3.0.RC.3"` instead.

For a single dependency that includes all provider clients (Anthropic, Gemini, Vertex AI, Perplexity, token counting):

```
"io.cequence" %% "openai-scala-all" % "1.3.0.RC.3"
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
    deploymentId = "your-deployment-id", // usually model name such as "gpt-35-turbo"
    apiVersion = "2023-05-15",           // newest version
    apiKey = "your_api_key"
  )
```

- For **Amazon Bedrock** via the [`bedrock-mantle`](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html) endpoint, which exposes the OpenAI **Responses API** (and Chat Completions for the gpt-oss family) with simple bearer-token auth — no AWS SigV4 signing. Requires a Bedrock API key (`AWS_BEARER_TOKEN_BEDROCK`) and region (`AWS_BEDROCK_REGION`).

```scala
  // OpenAI provider models (e.g. "openai.gpt-5.5") are served from the `openai/v1` base path
  val service = OpenAIServiceFactory.forBedrockMantle(isOpenAIModel = true)
  service.createModelResponse(Inputs.Text("What is the capital of France?"),
    settings = CreateModelResponseSettings(model = NonOpenAIModelId.bedrock_openai_gpt_5_5))

  // other models (e.g. "openai.gpt-oss-120b") use the standard `v1` base path
  val service = OpenAIServiceFactory.forBedrockMantle()
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

3. [Google Vertex AI](https://cloud.google.com/vertex-ai) - requires `openai-scala-google-vertexai-client` lib and `VERTEXAI_LOCATION` + `VERTEXAI_PROJECT_ID`
```scala
  val service = VertexAIServiceFactory.asOpenAI()
```

4. [Google Gemini](https://ai.google.dev/) - requires `openai-scala-google-gemini-client` lib and `GOOGLE_API_KEY`
```scala
  val service = GeminiServiceFactory.asOpenAI()
```

5. [Perplexity Sonar](https://www.perplexity.ai/) - requires `openai-scala-perplexity-client` lib and `SONAR_API_KEY`
```scala
  val service = SonarServiceFactory.asOpenAI()
```

6. [Novita](https://novita.ai/) - requires `NOVITA_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.novita)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.novita)
```

7. [Groq](https://wow.groq.com/) - requires `GROQ_API_KEY"`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.groq)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.groq)
```

8. [Grok](https://x.ai) - requires `GROK_API_KEY"`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.grok)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.grok)
```

9. [Fireworks AI](https://fireworks.ai/) - requires `FIREWORKS_API_KEY"`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.fireworks)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.fireworks)
```

10. [Octo AI](https://octo.ai/) - requires `OCTOAI_TOKEN`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.octoML)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.octoML)
```

11. [TogetherAI](https://www.together.ai/)  requires `TOGETHERAI_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.togetherAI)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.togetherAI)
```

12. [Cerebras](https://cerebras.ai/)  requires `CEREBRAS_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.cerebras)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.cerebras)
```

13. [Mistral](https://mistral.ai/) requires `MISTRAL_API_KEY`
```scala
  val service = OpenAIChatCompletionServiceFactory(ChatProviderSettings.mistral)
  // or with streaming
  val service = OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.mistral)
```

14. [Ollama](https://ollama.com/)
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

- Note that services with additional streaming support - `createCompletionStreamed` and `createChatCompletionStreamed` provided by [OpenAIStreamedServiceExtra](./openai-client-stream/src/main/scala/io/cequence/openaiscala/service/OpenAIStreamedServiceExtra.scala) (requires `openai-scala-client-stream` lib)

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
  service.retrieveModel(ModelId.gpt_5_4).map(model =>
    println(model.getOrElse("N/A"))
  )
```

- Create chat completion 

```scala
  val createChatCompletionSettings = CreateChatCompletionSettings(
    model = ModelId.gpt_5_4
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
    settings = CreateChatCompletionSettings(ModelId.gpt_5_4)
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
        model = ModelId.gpt_5_2,
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
        model = ModelId.gpt_5_2,
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
        model = ModelId.gpt_5_2
      ),
      failoverModels = Seq(ModelId.gpt_5_1, ModelId.gpt_5),
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
        model = ModelId.gpt_5_2, // Primary model
        max_tokens = Some(1000),
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(jsonSchemaDef)
      ),
      failoverModels = Seq(
        ModelId.gpt_5_1,  // First fallback model
        ModelId.gpt_5     // Second fallback model
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
        model = ModelId.gpt_5_mini,
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
        model = ModelId.gpt_5_mini,
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
        model = ModelId.gpt_5_mini,
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
        model = ModelId.gpt_5_mini,
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
    model = ModelId.gpt_4o_mini_2024_07_18,
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
    val model = ModelId.gpt_4_turbo_2024_04_09

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
    val model = ModelId.gpt_4_turbo_2024_04_09
    
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
        groqService -> Seq(NonOpenAIModelId.llama_3_3_70b_versatile),
        anthropicService -> Seq(
          NonOpenAIModelId.claude_fable_5,
          NonOpenAIModelId.claude_sonnet_4_6,
          NonOpenAIModelId.claude_haiku_4_5
        )
      ),
      openAIService
    )
```

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
    serviceModels = Map(geminiService -> Seq(NonOpenAIModelId.gemini_2_5_flash)),
    anthropicService
  )

  // routed by settings.model on submit, and by the explicit `model` arg on status/results/cancel/delete
  val batch   = router.createChatCompletionBatch(requests, CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash))
  val results = router.retrieveChatCompletionBatchResults(batch.id, NonOpenAIModelId.gemini_2_5_flash)
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
      geminiService -> Seq(NonOpenAIModelId.gemini_2_5_flash), // native batch
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
"io.cequence" %% "openai-scala-claude-agent-client" % "1.3.0.RC.3"
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

Created and maintained by [Peter Banda](https://peterbanda.net).
