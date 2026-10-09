# Adapters 🧩

[← back to the README](../README.md)

Composable service adapters: load distribution, logging, retries, routing by model, batch processing, chat-to-completion,
interception, input / output transformation and guardrails.

Adapters for OpenAI services (chat completion, core, or full) are provided by [OpenAIServiceAdapters](../openai-core/src/main/scala/io/cequence/openaiscala/service/adapter/OpenAIServiceAdapters.scala). The adapters are used to distribute the load between multiple services, retry on transient errors, route, guard calls with guardrails, or provide additional functionality. See [examples](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/adapters) for more details.

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
- **Retry** on a specific function using [RetryHelpers](../openai-core/src/main/scala/io/cequence/openaiscala/RetryHelpers.scala) directly
 
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

- **Batch processing** - provider-agnostic, ~50% of standard cost, async (typically a 24h turnaround target).
  Available on the full OpenAI service and on the Anthropic, Anthropic Bedrock, Gemini, and Vertex AI adapters
  (see the **Batch** column in the provider table above). It is an **opt-in capability**
  ([`OpenAIChatCompletionBatchService`](../openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIChatCompletionBatchService.scala)),
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

- **Batch router** - the batch-aware sibling of `chatCompletionRouter`, routing the batch endpoints across
  providers by model. Every registered service (and the default) must be batch-capable, and it respects the adapter's
  service type: `forFullService.chatCompletionBatchRouter(...)` returns an `OpenAIService` whose chat completion and
  batch are routed by model while files/assistants/etc. still delegate to the default service. Ideal for a central
  batch registry - submit through the router, persist `(model, batchId)`, and rebuild the identical router later to
  poll. See [ChatCompletionBatchRegistryPollingDemo](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/ChatCompletionBatchRegistryPollingDemo.scala).

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
  See [examples](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/adapters).

- **Guardrails** - `guardrails(input, output, ...)` checks a chat call's messages before it is made and its replies before
  they are returned. A [ModelGuardrail](../openai-core/src/main/scala/io/cequence/openaiscala/service/guardrails/ModelGuardrail.scala)
  asks all its checks in ONE call to any `json_schema`-capable chat model (one yes/no property per check): an LLM, or a
  decision model through `TypeSafeServiceFactory.asOpenAI` (Jev answers in ~250 ms, with a calibrated probability per
  check). The default input checks are prompt injection, system-prompt / config extraction, hate or harassment, sexual
  content, violence or self-harm, illegal activity, malware or hacking, and spam or abuse; the default output checks are a
  system-prompt leak, harmful content, and dangerous instructions (`personal_data` is available as well). You can add
  your own checks (`GuardrailCheck(name, description, threshold)`), a policy, and your own `InputGuardrail` /
  `OutputGuardrail` (a deny list, a PII detector, ...).
  - **Blocking:** a blocked call fails with an `OpenAIScalaGuardrailException` (`Reject`, the default), or is answered
    with a block message (`Respond`, finish reason `content_filter`; with several choices only the flagged ones).
  - **Retries:** a flagged reply can be asked for again (`outputReprompts`) - not when the call resumes a run paused
    for tool approval, whose decisions are one-shot.
  - **Guard failures:** a guardrail fails closed by default (`failOpen = false`).
  - **Reporting:** every verdict goes to `onVerdict`.
  - **Prompts:** the guard's system prompts are templates you can replace (`prompts = GuardrailPrompts(input, output)`,
    with the placeholders `{{policy}}` and `{{checks}}`); the defaults are `GuardrailPrompts.DefaultInput` /
    `DefaultOutput`.
  - **Scope:** `guardrails` guards `createChatCompletion` / `createChatToolCompletion` (and so the JSON helpers built on
    them). For a service that also streams, use `OpenAIServiceAdapters.guardrailsWithStreaming(...)`: it guards those
    calls and both streams, see below. Batches are not guarded.

```scala
  import io.cequence.openaiscala.domain.guardrails.{GuardrailAction, GuardrailCheck, ModelGuardrailSettings}
  import io.cequence.openaiscala.service.guardrails.ModelGuardrail

  // the guard: TypeSafe's Jev (or an LLM, e.g. ModelGuardrail(OpenAIServiceFactory(), ModelId.gpt_5_4_mini))
  val guard = ModelGuardrail(
    TypeSafeServiceFactory.asOpenAI(),
    ModelGuardrailSettings(
      TypeSafeModelId.jev_latest,
      inputChecks = GuardrailCheck.inputDefaults.map(_.withThreshold(0.7)), // decide by probability
      policy = Some("Questions about other companies' products are off-topic.")
    )
  )

  val service = OpenAIServiceAdapters.forFullService.guardrails(
    input = Seq(guard),
    output = Seq(guard),
    onViolation = GuardrailAction.Respond(), // "I can't help with this request. It was flagged by ..."
    onVerdict = verdict => if (verdict.violation) println(s"Blocked: ${verdict.flagged.map(_.name)}")
  )(OpenAIServiceFactory())

  // or check a message directly, e.g. alongside other work
  guard.checkInput(Seq(UserMessage(question))).map(verdict => verdict.violation)
```

  **Streams** - `guardrailsWithStreaming` starts a stream only once its input passed. With output guardrails, the
  reply is held back until the stream finishes, checked once, then released - so no unchecked text reaches the
  consumer, and `outputReprompts` works as without streaming. Without output guardrails the stream passes through as it
  comes. A block fails the stream (`Reject`), or answers it with the block message and finish reason `content_filter`
  (`Respond`; the typed stream also carries the verdicts in an `Other("guardrail_block")` chunk).

```scala
  import io.cequence.openaiscala.domain.guardrails.GuardrailPrompts

  val streamGuard = ModelGuardrail(
    TypeSafeServiceFactory.asOpenAI(),
    ModelGuardrailSettings(
      TypeSafeModelId.jev_latest,
      prompts = GuardrailPrompts(
        input = GuardrailPrompts.DefaultInput.replace("an AI assistant", "a bank's support assistant")
      )
    )
  )

  val streamingService = OpenAIServiceAdapters.guardrailsWithStreaming(
    input = Seq(streamGuard),
    output = Seq(streamGuard),
    onViolation = GuardrailAction.Respond()
  )(OpenAIServiceFactory.withStreaming())

  streamingService
    .createChatCompletionStreamedTyped(messages, settings)
    .runWith(Sink.foreach(println))
```

  See [GuardrailsSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/guardrails/GuardrailsSmokeTest.scala)
  for an LLM and a decision-model guard side by side, with and without streaming.
