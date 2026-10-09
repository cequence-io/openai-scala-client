# OpenAI Scala Client 🤖
[![version](https://img.shields.io/badge/version-1.5.0-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT) ![GitHub Stars](https://img.shields.io/github/stars/cequence-io/openai-scala-client?style=social) [![Follow on X](https://img.shields.io/badge/X-%400xbnd-black?logo=x)](https://x.com/0xbnd) ![GitHub CI](https://github.com/cequence-io/openai-scala-client/actions/workflows/continuous-integration.yml/badge.svg)

This is a no-nonsense async Scala client for OpenAI API and multiple LLM providers supporting all the available endpoints and params **including streaming** (with a provider-neutral typed stream of text / thinking / tool-call / tool-result chunks), **chat completion**, **responses API**, **agents API**, **tools** (including MCP), **graders**, **vision** (with provider-uniform file/image attachments), **batch processing**, and **voice routines** (as defined [here](https://platform.openai.com/docs/api-reference)), provided in a single, convenient service called [OpenAIService](./openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala) with adapters for Anthropic (incl. Bedrock and Managed Agents), Google Gemini/Vertex AI, Groq, Perplexity (incl. its decision model), TypeSafe AI (Jev), Liquid AI (d1), the decision models on OpenRouter, and others. The supported calls are:

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
* ⚠️ **Assistants, Threads, Thread Messages, Runs and Run Steps** are deprecated since 1.4.0 - OpenAI shut the Assistants API down on 2026-08-26; use the Responses API (`createModelResponse` / `createModelResponseStreamed`) instead
* **Assistants**: [createAssistant](https://platform.openai.com/docs/api-reference/messages/createMessage), [listAssistants](https://platform.openai.com/docs/api-reference/assistants/listAssistants), [retrieveAssistant](https://platform.openai.com/docs/api-reference/assistants/retrieveAssistant), [modifyAssistant](https://platform.openai.com/docs/api-reference/assistants/modifyAssistant), [deleteAssistant](https://platform.openai.com/docs/api-reference/assistants/deleteAssistant), and `deleteAssistantFile`
* **Threads**: [createThread](https://platform.openai.com/docs/api-reference/threads/createThread), [retrieveThread](https://platform.openai.com/docs/api-reference/threads/getThread), [modifyThread](https://platform.openai.com/docs/api-reference/threads/modifyThread), and [deleteThread](https://platform.openai.com/docs/api-reference/threads/deleteThread)
* **Thread Messages**: [createThreadMessage](https://platform.openai.com/docs/api-reference/assistants/createAssistant), [retrieveThreadMessage](https://platform.openai.com/docs/api-reference/messages/getMessage), [modifyThreadMessage](https://platform.openai.com/docs/api-reference/messages/modifyMessage), [listThreadMessages](https://platform.openai.com/docs/api-reference/messages/listMessages), [retrieveThreadMessageFile](https://platform.openai.com/docs/api-reference/messages/getMessageFile), [listThreadMessageFiles](https://platform.openai.com/docs/api-reference/messages/listMessageFiles), and [deleteThreadMessage](https://platform.openai.com/docs/api-reference/messages/deleteMessage)
* **Runs**: [createRun](https://platform.openai.com/docs/api-reference/runs/createRun), [createThreadAndRun](https://platform.openai.com/docs/api-reference/runs/createThreadAndRun), [listRuns](https://platform.openai.com/docs/api-reference/runs/listRuns), [retrieveRun](https://platform.openai.com/docs/api-reference/runs/retrieveRun), [modifyRun](https://platform.openai.com/docs/api-reference/runs/modifyRun), [submitToolOutputs](https://platform.openai.com/docs/api-reference/runs/submitToolOutputs), and [cancelRun](https://platform.openai.com/docs/api-reference/runs/cancelRun)
* **Run Steps**: [listRunSteps](https://platform.openai.com/docs/api-reference/run-steps/listRunSteps), and [retrieveRunStep](https://platform.openai.com/docs/api-reference/run-steps/getRunStep) 
* **Vector Stores**: [createVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/create), [listVectorStores](https://platform.openai.com/docs/api-reference/vector-stores/list), [retrieveVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/retrieve), [modifyVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/modify), and [deleteVectorStore](https://platform.openai.com/docs/api-reference/vector-stores/delete)
* **Vector Store Files**: [createVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/createFile), [listVectorStoreFiles](https://platform.openai.com/docs/api-reference/vector-stores-files/listFiles), [retrieveVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/getFile), and [deleteVectorStoreFile](https://platform.openai.com/docs/api-reference/vector-stores-files/deleteFile)  
* **Responses**: [createModelResponse](https://platform.openai.com/docs/api-reference/responses/create) (with tools support), [getModelResponse](https://platform.openai.com/docs/api-reference/responses/get), [deleteModelResponse](https://platform.openai.com/docs/api-reference/responses/delete), [cancelModelResponse](https://platform.openai.com/docs/api-reference/responses/cancel), [getModelResponseInputTokenCounts](https://platform.openai.com/docs/api-reference/responses/token-counts), [listModelResponseInputItems](https://platform.openai.com/docs/api-reference/responses/input-items), and [createModelResponseStreamed](https://platform.openai.com/docs/api-reference/responses-streaming) (typed events or `ChatChunk`s)
* **Graders**: [runGrader](https://platform.openai.com/docs/api-reference/graders/run), and [validateGrader](https://platform.openai.com/docs/api-reference/graders/validate)

The Anthropic client additionally covers the native **Messages** (incl. typed stream events), **Message Batches**, **Files**, **Skills**, and the whole **Managed Agents** API surface (agents, environments and their work queue, sessions, deployments, vaults and credentials, memory stores) - see the [Anthropic Managed Agents](docs/anthropic-managed-agents.md) page.

Note that in order to be consistent with the OpenAI API naming, the service function names match exactly the API endpoint titles/descriptions in camelCase.
Also, we aimed for the library to be self-contained with the fewest dependencies possible. Therefore, we implemented our own generic WS client (currently with Play WS backend, which can be swapped for other engines in the future). Additionally, if dependency injection is required, we use the `scala-guice` library.

---

👉 **No time to read a lengthy tutorial? Sure, we hear you! Check out the [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples) to see how to use the lib in practice.**

👉 **Follow [@0xbnd](https://x.com/0xbnd) on X for release announcements and LLM provider news.**

---

In addition to OpenAI, this library supports many other LLM providers. For providers that aren't natively compatible with the chat completion API, we've implemented adapters to streamline integration (see [examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples)).

| Provider | Structured output | Tools | Batch | Notes |
|----------|-------------------|-------|-------|-------|
| [OpenAI](https://platform.openai.com) | Full | Yes, Responses API too | Yes | Full API support |
| [Azure OpenAI](https://azure.microsoft.com/en-us/products/ai-services/openai-service) | Full | Yes, Responses API too | Yes | OpenAI on Azure |
| [Anthropic](https://www.anthropic.com/api) | Full | Yes, MCP and Skills too | Yes | Claude models |
| [Anthropic Bedrock](https://aws.amazon.com/bedrock/claude/) | Full | Yes, MCP too | Yes ¹ | Claude on AWS |
| [OpenAI Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html) | Full | Yes, Responses API too | – | GPT-5.x, gpt-oss, Grok and more on AWS ² |
| [Azure AI](https://azure.microsoft.com/en-us/products/ai-studio) | Varies | – | – | Open-source models |
| [Cerebras](https://cerebras.ai/) | Full ³ | Yes | – | Fast inference |
| [Deepseek](https://deepseek.com/) | JSON object mode only | – | – | Chinese provider |
| [FastChat](https://github.com/lm-sys/FastChat) | Varies | – | – | Local LLMs |
| [Fireworks AI](https://fireworks.ai/) | Full | – | – | Cloud provider |
| [Google Gemini](https://ai.google.dev/) | Full | Yes | Yes | Google's models |
| [Google Vertex AI](https://cloud.google.com/vertex-ai) | Full | Yes | Yes | Gemini models |
| [Grok](https://x.ai/) | Full | Yes | – | x.AI models |
| [Groq](https://wow.groq.com/) | Full ³ | Yes, MCP and server-side tools too | Yes | Fast inference |
| [MiniMax](https://www.minimax.io/) | Varies ⁴ | – | – | Chinese provider (global and China) |
| [Mistral](https://mistral.ai/) | Full | – | – | Open-source leader |
| [Novita](https://novita.ai/) | Model-dependent | – | – | Cloud provider |
| [Octo AI](https://octo.ai/) | JSON object mode only | – | – | Cloud provider (obsolete) |
| [Ollama](https://ollama.com/) | Varies | – | – | Local LLMs |
| [Perplexity](https://www.perplexity.ai/) | Implied only | – | – | Agent API ⁵ |
| [TogetherAI](https://www.together.ai/) | Model-dependent | – | – | Cloud provider |

¹ No prompt caching in batches.\
² Through `bedrock-mantle` or `bedrock-runtime`.\
³ Cerebras: `gpt-oss-120b`, `qwen-3.8-27b`; Groq: `openai/gpt-oss-*`, `qwen/qwen3.x-27b`.\
⁴ `json_schema` on M2.7 and newer, per MiniMax's docs.\
⁵ 🔥 New in 1.4.0. Perplexity's Sonar chat completions retired on 2026-09-27 (see [Services and providers](docs/providers.md)).

**Decision models** give typed answers with calibrated probabilities. They are served through the TypeSafe module
(`TypeSafeServiceFactory`), with `json_schema` structured output only - no tools or batch. See
[Decision-model providers](docs/decision-models.md#decision-model-providers-).

| Provider | Models | Factory method |
|----------|--------|----------------|
| [TypeSafe AI](https://typesafe.ai/) | Jev | `asOpenAI()` |
| [Liquid AI](https://www.liquid.ai/) 🔥 | d1, images too (the paid `d1`) | `liquidAsOpenAI()` |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) (local) 🔥 | Liquid's Open d1 (d1-3B, d1-omni-600M) once llama.cpp loads them; Julia-1, Laya, Lev, Kev, OpenJev, Nimble, Clef | `asOpenAI(DecisionProviderSettings.llamaCpp)` |
| [Perplexity Decisions](https://docs.perplexity.ai/docs/decisions/quickstart) 🔥 | pplx-decider-v1.1-27b / v1-27b, images too | `perplexityAsOpenAI()` |
| [OpenRouter](https://openrouter.ai/models?output_modalities=decisions) 🔥 | Jev, d1, Perplexity's deciders, OpenAI's Luna, Cloudflare's Clef, Solar Decide, Mercury Decide, Tev1, Kev 4B, Span-01 | `asOpenAI(DecisionProviderSettings.openRouter)` |
| [OpenAI Decisions](https://developers.openai.com/api/docs/guides/decisions) 🔥 | gpt-6-luna, images too | `asOpenAI(DecisionProviderSettings.openAI)` |

🔥 New in 1.5.0 (Liquid AI's d1 since 1.4.0, its images since 1.5.0).

---

👉 For background information how the project started read an article about the lib/client on [Medium](https://medium.com/@0xbnd/openai-scala-client-is-out-d7577de934ad).

Also try out our [Scala client for Pinecone vector database](https://github.com/cequence-io/pinecone-scala), or use both clients together! [This demo project](https://github.com/cequence-io/pinecone-openai-scala-demo) shows how to generate and store OpenAI embeddings into Pinecone and query them afterward. The OpenAI + Pinecone combo is commonly used for autonomous AI agents, such as [babyAGI](https://github.com/yoheinakajima/babyagi) and [AutoGPT](https://github.com/Significant-Gravitas/Auto-GPT).

**✔️ Important**: this is a "community-maintained" library and, as such, has no relation to OpenAI company.

## Installation 🚀

The currently supported Scala versions are **2.12, 2.13**, and **3**.  

To install the library, add the following dependency to your *build.sbt*

```
"io.cequence" %% "openai-scala-client" % "1.5.0"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>io.cequence</groupId>
    <artifactId>openai-scala-client_2.12</artifactId>
    <version>1.5.0</version>
</dependency>
```

If you want streaming support, use `"io.cequence" %% "openai-scala-client-stream" % "1.5.0"` instead.

For a single dependency that includes all provider clients (Anthropic, Gemini, Vertex AI, Perplexity, TypeSafe AI, token counting):

```
"io.cequence" %% "openai-scala-all" % "1.5.0"
```

## Config ⚙️

- Env. variables: `OPENAI_SCALA_CLIENT_API_KEY` and optionally also `OPENAI_SCALA_CLIENT_ORG_ID` (if you have one)
- File config (default):  [openai-scala-client.conf](./openai-client/src/main/resources/openai-scala-client.conf)
- Streams: one frame (a server-sent event or JSON line) may take up to 32 MiB - a generated image in base64 arrives in one
  event. Change the cap with `openai-scala-client.streaming.maxFrameLength` (e.g. `128 MiB`) or the env. variable
  `OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH`; a larger frame fails the stream with an exception naming both the
  setting and the variable (see the [FAQ](docs/faq.md)).

## Quick start 🏁

```scala
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory

import scala.concurrent.ExecutionContext.Implicits.global

val service = OpenAIServiceFactory() // the key from OPENAI_SCALA_CLIENT_API_KEY

service
  .createChatCompletion(
    Seq(UserMessage("What is the capital of Norway? One word.")),
    CreateChatCompletionSettings(model = ModelId.gpt_5_4_mini)
  )
  .map(response => println(response.contentHead))
  .andThen { case _ => service.close() } // close a service when done with it - it owns an HTTP pool and an actor system
```

Every provider in the tables above is reached the same way - through its factory's `asOpenAI()` (or
`OpenAIChatCompletionServiceFactory(ChatProviderSettings.<provider>)`), after which the calls, adapters and streams are the
same. See [Services and providers](docs/providers.md).

## Documentation 📚

| Page | What it covers |
|------|----------------|
| [Services and providers](docs/providers.md) | Factories, config and keys; every provider's adapter - Azure, Anthropic and Bedrock, Gemini and Vertex AI, Mistral, Groq, Grok, Cerebras, DeepSeek, Perplexity, Liquid AI, any OpenAI-compatible endpoint |
| [Chat completions](docs/chat-completions.md) | Calls, JSON / structured output (schemas from case classes), failover, tools, reasoning effort, files and images |
| [Responses API](docs/responses-api.md) | Inputs and images, file / web search, function calls, MCP, multi-agent execution |
| [Agents API](docs/agents-api.md) | Durable cloud agents (beta): sessions, events, the chat adapter |
| [Streaming](docs/streaming.md) | OpenAI-shaped and typed streams (`ChatChunk`), the frame cap, human approval mid-stream |
| [Decision models](docs/decision-models.md) | OpenAI's Decisions API, TypeSafe (Jev), Liquid (d1), Perplexity's decider, OpenRouter, llama.cpp; either API on either host, typed decisions, re-ranking |
| [Adapters](docs/adapters.md) | Round robin, retry, logging, routing, batch processing, interception, guardrails |
| [Sharing an HTTP engine](docs/http-engine.md) | One engine for many services and providers; timeouts and proxies |
| [Graders API](docs/graders-api.md) | Evaluating model outputs |
| [Anthropic Managed Agents](docs/anthropic-managed-agents.md) | Server-hosted agents with tool-permission pauses |
| [Claude Agent Client](docs/claude-agent-client.md) | The `claude` CLI as a subprocess transport |
| [FAQ](docs/faq.md) | Timeouts, errors, the stream frame cap, ... |

Ready-to-run examples for everything above are in
[openai-examples](./openai-examples/src/main/scala/io/cequence/openaiscala/examples); the release notes are in
[CHANGELOG.md](./CHANGELOG.md).

## License ⚖️

This library is available and published as open source under the terms of the [MIT License](https://opensource.org/licenses/MIT).

## Contributors 🙏

This project is open-source and welcomes any contribution or feedback ([here](https://github.com/cequence-io/openai-scala-client/issues)).

Development of this library has been supported by  [<img src="https://cequence.io/favicon-16x16.png"> - Cequence.io](https://cequence.io) - `The future of contracting` 

Created and maintained by [Peter Banda](https://peterbanda.net) (on X [here](https://x.com/0xbnd)).
