# Chat completions 💬

[← back to the README](../README.md)

Calling the service: models, chat completions, JSON / structured output (schemas written by hand or derived from a
case class), failover, provider tools, reasoning effort, and files / images as attachments.

Full documentation of each call with its respective inputs and settings is provided in [OpenAIService](../openai-core/src/main/scala/io/cequence/openaiscala/service/OpenAIService.scala). Since all the calls are async they return responses wrapped in `Future`.

There is a new project [openai-scala-client-examples](../openai-examples/src/main/scala/io/cequence/openaiscala/examples) where you can find a lot of ready-to-use examples!

- List models

```scala
  service.listModels.map(models =>
    models.foreach(println)
  )
```

  Only a model's `id` is certain: `created` and `owned_by` are options, since many OpenAI-compatible hosts leave one
  out (Gemini's OpenAI endpoint, DeepSeek, SambaNova and Cohere send no `created`, OpenRouter and Together no
  `owned_by`). Together's bare-array listing and Anthropic's `created_at` dates are read too.

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

- 🔥 **Tool loops that keep the model's reasoning**. Every adapter that gets reasoning from its provider reads it into
  the reply as a provider-neutral `ReasoningMessage` (`choice.reasoning`; `response.reasoningMessage` for the first
  choice). Append `response.historyMessages` instead of the bare assistant message: that is the `ReasoningMessage`
  followed by the assistant message. The next round converts it back to the provider's own form and sends it before
  the reply, so the model keeps its chain of thought between rounds.

  A `ReasoningBlock` is one case class whose fields are set as the provider returned them:

  | Backend | Read from the reply (`ReasoningBlock` fields) | Sent back as |
  |---|---|---|
  | OpenAI Responses API (OpenAI, Bedrock's OpenAI models) | reasoning items: `id`, `summary`, `encrypted` (+ `text` where returned) | reasoning items |
  | Anthropic API / Bedrock | thinking blocks: `text` + `signature`; redacted thinking: `encrypted` | thinking blocks opening the assistant turn |
  | Gemini / Vertex AI | thought parts: `text` (+ `signature`) - sync calls only with `setGeminiIncludeThoughts()` / `setVertexAIIncludeThoughts()` (no `reasoning_effort` needed) | thought parts opening the model turn |
  | Chat completions providers (DeepSeek, Fireworks, Mistral, Cerebras, Groq, ...) | `reasoning_content` / `reasoning`, Mistral's thinking chunks: `text` + `field` | the same field, or a thinking chunk |

  The full OpenAI service sends tool calls of OpenAI's reasoning models to the Responses API (GPT-6 Astra / 6.1 Sol
  always; GPT-6 Sol / Luna, 6.1 Terra and GPT-5.6+ unless `reasoning_effort` is `none`; GPT-5.4 / 5.5 with an effort other than
  `none`), and asks for their reasoning encrypted, since the adapter stores nothing (`store = false`).

  OpenAI's reasoning is encrypted, not readable. For a readable version ask for reasoning summaries with
  `settings.setResponsesReasoningSummary()`: the reply's `reasoningMessage.text` (the blocks' `summary`) - also
  without a `reasoning_effort` (the model's default effort) and also without tools: the full service sends any call
  carrying it for an OpenAI reasoning model through the Responses API (the chat completions API returns no reasoning
  text for OpenAI's models; a chat-only service ignores the flag). The typed stream asks for summaries by default
  whenever `reasoning_effort` is set. An easy question may get an empty summary (live 2026-10-26: GPT-6 Luna
  summarized nothing on a one-step question, ~450 characters on a counting puzzle), and now and then a reasoned one does
  too (2026-10-29: one of three identical calls).

  Where the reasoning goes:
  - Back only to the service that returned it (a Responses / Anthropic / Gemini / Vertex AI service instance - the
    merged `OpenAIServiceFactory.withStreaming()` service counts as one, its typed streams carry its sync calls'
    reasoning; for the chat completions API, its base URL and credentials). Reasoning is bound to the provider and account that made it:
    OpenAI's is a 400 on Bedrock and Bedrock's a 400 on OpenAI (live 2026-10-26). Build the service once and share it,
    as usual: a service remembers its last 16,384 reasoning blocks.
  - Another model on the same service is fine: OpenAI and Bedrock accept it (GPT-6 Luna, 6.1 Sol, 5.4-mini), and so
    does Claude - Sonnet 5.5 and Opus 5.5 read Haiku 5.5's thinking, Haiku 5.5 reads Haiku 4.5's, Haiku 4.5 ignores
    5.5's; nothing is refused, not even by a round with thinking turned off (live 2026-10-26).
  - Any other service or backend leaves the `ReasoningMessage` out, so the same history survives a failover. The new
    model just reasons afresh.
  - Streams do not produce a `ReasoningMessage` yet.

```scala
  def loop(messages: Seq[BaseMessage]): Future[ChatToolCompletionResponse] =
    service
      .createChatToolCompletion(messages, tools, None, CreateChatCompletionSettings(ModelId.gpt_6_luna))
      .flatMap { response =>
        val calls = response.assistantToolMessage.tool_calls
        if (calls.isEmpty)
          Future.successful(response)
        else
          loop(
            messages ++ response.historyMessages ++ calls.map { case (id, call: FunctionCallSpec) =>
              ToolMessage(Some(runTool(call)), id, call.name)
            }
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

- **How `createChatCompletionWithJSON` picks its mode** - the model is looked up in `models-supporting-json-schema`
  (`openai-scala-client.conf`, or the call's own `jsonSchemaModels`). A listed model gets native structured output -
  `response_format: json_schema`, the schema enforced by the provider. An unlisted one gets `response_format: json_object`
  with the schema appended to the prompt, the reply parsed and repaired on the client: weaker (nothing is enforced
  server-side), but it works for any model that can follow a schema in text. So the list selects the mechanism, not whether
  the call works; keep it current for the models you want enforced. `enforceMode` overrides the list for a call:
  `Some(ChatCompletionResponseFormatType.json_schema)` sends the schema natively to a model the list misses, and
  `Some(ChatCompletionResponseFormatType.json_object)` puts it in the prompt even for a listed model. The latter helps
  - with **prompt caching on GPT-6**, when calls share a long prompt but differ in schema: GPT-6 caches a native schema
    as part of the prompt's prefix, so a call with another schema reads nothing from the cache, while in JSON-object mode
    the schema rides at the end of the last user message and the shared prompt stays cached (live 2026-10-11, a
    3,300-token prompt, a second call with another schema: gpt-6-luna and gpt-6-sol 0 tokens cached natively vs 3,231 in
    JSON-object mode; gpt-5.4-mini and gpt-5.5 cached 2,816 either way);
  - with a provider whose structured output loses parts of a schema (Anthropic refuses `minimum` / `maximum`, so its
    adapter strips them; Gemini ignores them and `enum`), while the prompt shows the model the whole schema.

  `createChatCompletionBatchWithJSON` and `ModelGuardrailSettings` take the same switch, and
  [`JsonEnforceModeSmokeTest`](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/JsonEnforceModeSmokeTest.scala)
  runs all three ways on four providers. Decision models are the one case where both routes are equivalent: their adapter
  turns the schema into questions either way (reading it back from the prompt in the fallback), so an unlisted decider id
  loses nothing.

```scala
  // the schema at the end of the prompt, although gpt-6-sol takes it natively - the shared
  // system prompt stays cached across calls with different schemas
  service
    .createChatCompletionWithJSON[JsObject](
      messages = messages,
      settings = CreateChatCompletionSettings(
        model = ModelId.gpt_6_sol,
        jsonSchema = Some(jsonSchemaDef)
      ),
      enforceMode = Some(ChatCompletionResponseFormatType.json_object)
    )
```

- **JSON schema derived from a case class** - `jsonSchemaFor[T]()` (`JsonSchemaReflectionHelper`; runtime reflection on
  Scala 2, a macro on Scala 3 - the same call and the same schema on both). Fields map to their JSON types (collections =
  arrays, nested case classes = objects, type parameters resolved); `Enumeration`s, Java enums, Scala 3 `enum`s and
  sealed traits of case objects become string enums; `@JsonSchemaDescription` and `@JsonSchemaRange(min, max)` add
  descriptions and numeric bounds, 🔥 `@JsonSchemaIgnore` leaves a field out (one the model is never asked for - it
  must be an `Option` or have a default value; a default needs `Json.using[Json.WithDefaultValues].format` to read the
  answer). 🔥 An `Option` field is REQUIRED and may be null (`JsonSchema.Nullable`, a union of its type and null -
  JSON Schema's `anyOf`, `JsonSchema.AnyOf` - written as `{"anyOf": [<type>, {"type": "null"}]}`), so a class with
  optional fields takes OpenAI's strict mode, and the model
  answers `null` where it has nothing - `Json.format` reads that as `None` (`jsonSchemaFor(nullableOptions = false)`
  derives an `Option` as before 1.7.0: its type, not required - which strict mode refuses). Measured 2026-11-03 with
  strict `json_schema`: OpenAI, Anthropic, Gemini's OpenAI endpoint, Mistral, Groq, Fireworks, Together, Cerebras,
  xAI and Meta all take the `anyOf` form and answer null or a value. The native Gemini and Vertex AI adapters send an
  `Option` field as its type with `nullable: true` (Gemini answers an `anyOf` null branch with "" or "None"), other
  unions as their own `anyOf`; a decision model asks the field as its type (it always answers). Function tools: a
  strict tool goes out required and nullable, its objects closed; a non-strict one as given, except to Cerebras,
  Together and Meta's Model API, which mishandle a nullable union in tool parameters and get the `Option` fields the
  pre-1.7.0 way (optional, of their type); Gemini gets tool parameters as a JSON Schema. 🔥 A sealed trait's case objects keep their declaration order
  when each level of the hierarchy is declared in one object (typically the trait's companion); case objects at a
  package's top level are sorted by name on both Scala versions (Scala 2's runtime reflection cannot see their
  order). A described case object or enum case adds a
  "- value: description" line to its field's description, since a JSON schema enum has no place for value descriptions. The commonly used Scala and Java types are covered: the boxed and `java.math` numbers, `UUID` / `URI` / `URL` / `Locale` /
  `Currency` / `File` / `Path`, the `java.time` values, `Duration`s and `Period`s (Scala's `Duration` too) as strings,
  `java.util.Optional` like `Option`, `java.util.List` / `Set` / any Java `Iterable` as arrays, a value class
  (`extends AnyVal`) as its underlying type (as `Json.valueFormat` writes it), and a `Map[String, V]` / `java.util.Map` as an
  open object (`additionalProperties: true`; the value type is not expressed, and OpenAI's strict mode closes every object -
  a map needs `strict = false`). An `Either`, a tuple, a sealed hierarchy with case classes or a recursive type is refused
  (Scala 3: at compile time); an `Enumeration` declared in a class stays a plain string. APIs that need a type's schema take it as a `JsonSchemaOf[T]`, derived the same
  way unless you put an instance of your own in scope (`JsonSchemaOf.instance(schema)`).

```scala
  import io.cequence.openaiscala.domain.{JsonSchemaDescription, JsonSchemaRange}
  import io.cequence.openaiscala.service.JsonSchemaReflectionHelper.jsonSchemaFor

  case class Country(
    country: String,
    @JsonSchemaDescription("The capital city") capital: String,
    @JsonSchemaRange(0, 2000) populationMil: Int
  )

  @JsonSchemaDescription("The countries, most populous first")
  case class CapitalsResponse(capitals: Seq[Country])

  implicit val countryFormat: Format[Country] = Json.format[Country]
  implicit val capitalsFormat: Format[CapitalsResponse] = Json.format[CapitalsResponse]

  service
    .createChatCompletionWithJSON[CapitalsResponse](
      messages,
      CreateChatCompletionSettings(
        model = ModelId.gpt_5_4_mini,
        jsonSchema = Some(JsonSchemaDef("capitals_response", strict = true, jsonSchemaFor[CapitalsResponse]()))
      )
    )
    .map(_.capitals.foreach(println))
```

- **Or let the JSON helper infer the schema from `T`** - with no `jsonSchema` in the settings, `createChatCompletionWithJSON[T]`
  (and `createChatCompletionWithJSONFullResponse[T]` / `createChatCompletionBatchWithJSON[T]`) asks with the schema of `T`:
  the case class above with its annotations, as a `JsonSchemaDef` named after the type (`CapitalsResponse`). It is
  `strict` unless OpenAI's strict mode would refuse it - a `Map` makes it `strict = false` (an `Option` field no longer
  does: it is a required field that may be null). The rules
  (`InferredJsonSchema`, the same on Scala 2 and 3):
  - the settings' own `jsonSchema` always wins, so schemas built at runtime work as before;
  - a `JsonSchemaOf[T]` of your own in scope is used instead of the derived schema
    (`JsonSchemaOf.instance("name", schema)` names it; an unnamed one is called `response`);
  - nothing is inferred for a JSON value (`JsObject`, `JsValue`), a type whose schema is not an object with fields (a
    `Seq`, a `String`, a `Map`, an enum) or one that does not derive (an `Either`, a sealed hierarchy of case classes) -
    the reply is parsed without a schema, as before. On Scala 3 such a type still compiles: the inference has a macro of
    its own that gives up quietly, where `decide[T]` reports a compile error;
  - generic code passes the inference on with a context bound, `def ask[T: Format: InferredJsonSchema]`, which the
    caller resolves for its concrete `T` (an abstract `T` without the bound infers nothing);
  - `implicit val noSchema: InferredJsonSchema[T] = InferredJsonSchema.none` keeps a derivable `T` schema-less.

```scala
  // no jsonSchema in the settings - the schema comes from CapitalsResponse
  service
    .createChatCompletionWithJSON[CapitalsResponse](
      messages,
      CreateChatCompletionSettings(model = ModelId.gpt_5_4_mini)
    )
    .map(_.capitals.foreach(println))
```

  [`CreateChatCompletionJsonInferredSchema`](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatCompletionJsonInferredSchema.scala)
  runs it on gpt-5.4-mini, Claude Haiku 5.5, Gemini 2.5 Flash-Lite and Jev - a decision model plans the inferred schema
  into its questions.

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


### Failover policy

🔥 Every failover call (`createChatCompletionWithFailover`, `createChatCompletionWithFailoverSettings`,
`createChatCompletionWithJSON`, `createChatCompletionWithJSONFullResponse`) takes a last parameter
`policy: FailoverPolicy`. The default changes nothing; the fields:

| Field | What it does |
| --- | --- |
| `attemptTimeout` | Limit of ONE HTTP try (not of a model's whole ladder of retries). A try over the limit is abandoned and the chain moves on to the next model. |
| `deadline` | A `scala.concurrent.duration.Deadline`: no new try once overdue; the time left also caps a try's limit. |
| `shouldStop` | A flag (`() => Boolean`) checked before every new try, also after a backoff sleep. |
| `parseRetries` | JSON helpers: how many times the SAME model is asked again (no backoff) after an unparseable answer. |
| `failoverOnParseError` | JSON helpers: after the re-asks go to the next model; otherwise the `OpenAIScalaJsonParseException` fails the call, as before. |
| `onAttempt` | An `AttemptListener` called with the `AttemptInfo` (requested model, model index, try number, kind: `First` / `TransientRetry` / `ParseReask`) and the `AttemptOutcome` (`Answered`, `Unparseable`, `Failed`, `TimedOut`, `LateAnswer`, `LateFailure`) of every try - for usage accounting. |
| `wrapAttempt` | An `AttemptWrapper` around the RAW call of every try (not the timeout race), e.g. an in-flight counter that still sees a timed-out call until it really lands. |
| `finalError` | Picks the error of a chain that ran out of models from each model's final error, in chain order. `FailoverPolicy.lastError` (default) or `FailoverPolicy.tokenCountExceededFirst`. |
| `failoverLogLevel` | `Throwable => FailoverLogLevel` (`Error` / `Warn` / `Info` / `Debug`): the level of the failover line after a model's last error, and of the final "no more models" line. `Error` logs the stack trace, the other levels the message plus `Reason: <error message>`. Default `FailoverLogLevel.default`: `Warn` for `OpenAIScalaModelSkippedException`, `Error` for everything else. |

Per try, in this order: the deadline is checked, then the stop flag (either one fails the chain with
`OpenAIScalaChainStoppedException`, whose `byDeadline` tells which, `attempts` how many tries were made and
whose cause is the most recent error); then the call is made with a limit of the smaller of `attemptTimeout` and the
time left to the deadline (rounded up to the millisecond, so no try starts after the deadline). A try over its limit
fails with `OpenAIScalaAttemptTimeoutException`, and a timed-out try is **never retried on the same model** (a hung
model tends to hang again, even with `retryOnAnyError`) - the chain fails over at once. The exception is deliberately
not `Retryable` (nor an `OpenAIScalaClientTimeoutException`): a retry around the whole call would run the chain again
while the abandoned calls still run. A synchronous throw of the call is not retried either.

A timed-out call keeps running, and the provider bills it. When it finally lands the listener gets `LateAnswer`
(with the response and its usage) or `LateFailure`. The listener may therefore be called **concurrently**, from other
threads, so it must be thread-safe; a throwing listener is logged and ignored. A caller that sums the usage of the
final response as well as the listener's outcomes must not count the answered try twice.

A guard that rules a model out for a request (a budget, a data-residency rule, a feature the model lacks) throws
`OpenAIScalaModelSkippedException(message, model)` - from `wrapAttempt`, synchronously or as a failed Future, or from a
decorating service. The chain fails over at once, **never retries it on the same model** (even with `retryOnAnyError`,
like a timeout), reports `Failed(skip)` to the listener and logs the failover at `WARN` without a stack trace. To give
your own exception a level, map it and fall back to the default:

```scala
  import io.cequence.openaiscala.OpenAIScalaModelSkippedException
  import io.cequence.openaiscala.service.{AttemptInfo, AttemptWrapper, FailoverLogLevel, FailoverPolicy}

  val policy = FailoverPolicy(
    wrapAttempt = (info: AttemptInfo, call: () => Future[ChatCompletionResponse]) =>
      if (overBudget(info.requestedModel))
        Future.failed(new OpenAIScalaModelSkippedException("over budget", info.requestedModel))
      else
        call(),
    failoverLogLevel = {
      case _: MyQuotaException => FailoverLogLevel.Info
      case e                   => FailoverLogLevel.default(e)
    }
  )
```

The JSON helpers choose the JSON mode **per model**: a failover model gets native `json_schema` or JSON-object mode
(the schema appended to the prompt) by `jsonSchemaModels` / `enforceMode` / the config's list for ITS OWN id, instead
of inheriting the primary model's choice. A failover model's request is prepared only when the chain reaches it; one
that cannot be prepared fails that model's try (reported as `Failed`) and the chain moves on.

```scala
  import io.cequence.openaiscala.service.{AttemptListener, AttemptOutcome, FailoverPolicy}
  import scala.concurrent.duration._

  val policy = FailoverPolicy(
    attemptTimeout = Some(30.seconds),
    deadline = Some(2.minutes.fromNow),
    parseRetries = 1,
    failoverOnParseError = true,
    onAttempt = (info, outcome) =>
      outcome match {
        case AttemptOutcome.Answered(r) => println(s"${info.requestedModel}: ${r.usage}")
        case AttemptOutcome.LateAnswer(r) => println(s"${info.requestedModel} (late, billed): ${r.usage}")
        case other => println(s"${info.requestedModel}: $other")
      },
    finalError = FailoverPolicy.tokenCountExceededFirst
  )

  service.createChatCompletionWithJSON[Capital](
    messages,
    CreateChatCompletionSettings(ModelId.gpt_5_5),
    failoverModels = Seq(ModelId.gpt_5_4_mini),
    policy = policy
  )
```

Live walkthrough: [FailoverPolicySmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/FailoverPolicySmokeTest.scala)
(timeout with a late answer, a JSON chain over a missing model, the stop flag, a deadline).


## Provider features: tools, reasoning, attachments

- **Anthropic** - tool use (requires `openai-scala-anthropic-client` lib). Supports tools such as
  `Tool.bash()`, `Tool.webSearch()`, `Tool.webFetch()`, `Tool.codeExecution()`, `Tool.computer()`,
  `Tool.custom()`, and MCP servers via `MCPServerURLDefinition`.
  See [examples](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/tools).

- **Reasoning effort** - `CreateChatCompletionSettings(reasoning_effort = Some(ReasoningEffort.high))` works across providers:
  OpenAI reasoning models take it natively (`none`/`minimal`/`low`/`medium`/`high`/`xhigh`, plus `max` on the Responses API),
  while the Anthropic, Gemini, and Vertex AI adapters translate it into `output_config.effort` / adaptive thinking
  (Claude) or `thinking_level` / `thinking_budget` (Gemini). The token budgets behind each level are configurable via
  `reasoning-effort-thinking-budget-mapping` in [openai-scala-client.conf](../openai-client/src/main/resources/openai-scala-client.conf).
  Unsupported combinations (e.g. sampling params on GPT-5.x/GPT-6, `minimal` on Gemini 3.7+/Pro) are downgraded automatically
  with a warning by [ChatCompletionSettingsConversions](../openai-core/src/main/scala/io/cequence/openaiscala/service/adapter/ChatCompletionSettingsConversions.scala)
  and the provider adapters.

- **Files and images as provider-uniform attachments** - `FileContent` (PDF) and `ImageURLContent` (JPEG/PNG/GIF/WebP, data URLs or http URLs)
  are accepted by the OpenAI, Anthropic (+ Bedrock), Gemini, and Vertex AI chat completion services. Because only OpenAI
  carries the filename on the wire, [VLMContent](../openai-core/src/main/scala/io/cequence/openaiscala/domain/VLMContent.scala)
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
  See the `*WithFileContentAndPdf`, `*WithMultipleNamedPdfsAsFileContent`, and `*VLMSmokeTest` [examples](../openai-examples/src/main/scala/io/cequence/openaiscala/examples).

- **Anthropic native streaming events** - besides the text-only `createMessageStreamed`, `AnthropicService.createMessageStreamedEvents`
  returns a typed `Source[MessageStreamEvent, NotUsed]` with every SSE event (message start/delta/stop, content block
  start/delta/stop incl. tool_use blocks, ping, usage), which the OpenAI adapter also uses to surface streamed tool calls and to build
  the typed `ChatChunk` stream. Note that Claude Opus 5 / Sonnet 5 / Fable 5.x default to `display = omitted` for thinking - set
  `ThinkingSettings.adaptiveSummarized` (or `thinking.withDisplay(ThinkingDisplay.summarized)`) to receive `thinking_delta` events on the
  native API; the typed stream does this for you. Streamed frames of up to 32 MiB are accepted (configurable - see the
  Config section), so large server-tool result blocks (web search, web fetch) do not break the stream.
