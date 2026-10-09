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

- **How `createChatCompletionWithJSON` picks its mode** - the model is looked up in `models-supporting-json-schema`
  (`openai-scala-client.conf`; or the call's own `jsonSchemaModels`, or `enforceJsonSchemaMode = true`). A listed model gets
  native structured output - `response_format: json_schema`, the schema enforced by the provider. An unlisted one gets
  `response_format: json_object` with the schema appended to the prompt, the reply parsed and repaired on the client: weaker
  (nothing is enforced server-side), but it works for any model that can follow a schema in text. So the list selects the
  mechanism, not whether the call works; keep it current for the models you want enforced. Decision models are the one case
  where both routes are equivalent: their adapter turns the schema into questions either way (reading it back from the
  prompt in the fallback), so an unlisted decider id loses nothing.
- **JSON schema derived from a case class** - `jsonSchemaFor[T]()` (`JsonSchemaReflectionHelper`; runtime reflection on
  Scala 2, a macro on Scala 3 - the same call and the same schema on both). Fields map to their JSON types (`Option` = not
  required, collections = arrays, nested case classes = objects, type parameters resolved); `Enumeration`s, Java enums,
  Scala 3 `enum`s and sealed traits of case objects become string enums; `@JsonSchemaDescription` and
  `@JsonSchemaRange(min, max)` add descriptions and numeric bounds. A described case object or enum case adds a
  "- value: description" line to its field's description, since a JSON schema enum has no place for value descriptions. The commonly used Scala and Java types are covered: the boxed and `java.math` numbers, `UUID` / `URI` / `URL` / `Locale` /
  `Currency` / `File` / `Path`, the `java.time` values, `Duration`s and `Period`s (Scala's `Duration` too) as strings,
  `java.util.Optional` like `Option`, `java.util.List` / `Set` / any Java `Iterable` as arrays, a value class
  (`extends AnyVal`) as its underlying type (as `Json.valueFormat` writes it), and a `Map[String, V]` / `java.util.Map` as an
  open object (`additionalProperties: true`; the value type is not expressed, and OpenAI's strict mode closes every object -
  a map needs `strict = false`). An `Either`, a tuple, a sealed hierarchy with case classes or a recursive type is refused
  (Scala 3: at compile time); an `Enumeration` declared in a class stays a plain string. OpenAI's strict mode requires every field, so use
  `strict = false` with `Option` fields. APIs that need a type's schema take it as a `JsonSchemaOf[T]`, derived the same
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
