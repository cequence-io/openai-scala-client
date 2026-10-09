# Decision models 🎯

[← back to the README](../README.md)

Decision models give typed answers - yes/no probabilities, choices, scores - with calibrated probabilities, in a few
hundred milliseconds. OpenAI's Decisions API is part of the full `OpenAIService`; TypeSafe AI's Jev, Liquid AI's d1,
Perplexity's decider, OpenRouter's catalogue and a local llama.cpp are served through the `typesafe-client` module,
which also serves either API on either host, typed decisions (`decide[T]`) and re-ranking.

## OpenAI Decisions API (gpt-6-luna) 🔥

- **Decisions API** (🔥 New, public beta) - typed answers about text and inline images, about 10x faster than asking a
  model through the Responses API: the probability that a condition holds (`Predicate`), one of fixed options
  (`Choice`) or a score against ordered levels (`Score`), each with its distribution. `gpt-6-luna` is the only model;
  input costs $0.10 per 1M tokens, output nothing.

```scala
  import io.cequence.openaiscala.domain.decisions._

  service
    .createDecision(
      DecisionInput.Text("I was charged twice for my order. Please refund the second charge!"),
      Seq(
        DecisionQuestion.Predicate("Does the customer ask for a refund?", Some("refund")),
        DecisionQuestion.Choice(
          "Which team should handle the ticket?",
          Seq(DecisionChoice("billing", "Payments, invoices, refunds."), DecisionChoice("technical", "Problems using the product.")),
          Some("team")
        ),
        DecisionQuestion.Score("How urgent is it?", Seq(DecisionLevel("low"), DecisionLevel("medium"), DecisionLevel("high")), Some("urgency"))
      )
    )
    .map { decision =>
      decision.answer("refund") // Some(Predicate(Some(refund), 1.0))
      decision.answer("team")   // Some(Choice(Some(team), Text(billing), ..., confidence = 1.0))
    }
```

  Images go in as `DecisionInput.of(DecisionContent.InputText(...), DecisionContent.InputImage(dataUrl))` (base64 data
  URLs only). A question the model declines is answered with a `DecisionAnswer.Refusal`, the others normally. Limits
  (live 2026-10-07): 200 questions, 255 choices and 10 levels per question. Typed decisions (`decide[T]`), re-ranking,
  `json_schema` structured output and decision-model guardrails run on it through the decision provider
  `DecisionProviderSettings.openAI` (see [Decision-model providers](#decision-model-providers-)).

  **Switching between OpenAI's and TypeSafe's decision APIs.** `OpenAIDecisionsService` is a service of its own, which
  `OpenAIService` extends, and code written against either API runs on either host - only the construction changes:

```scala
  // code written against OpenAI's Decisions API (createDecision) ...
  val decisions: OpenAIDecisionsService =
    if (useJev) TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.typeSafe) // TypeSafe's Jev, translated
    else OpenAIServiceFactory()                                                            // OpenAI, natively

  // ... and code written against TypeSafe's System One (systemOne, decide[T], rerank, asOpenAI)
  val decider: TypeSafeService =
    TypeSafeServiceFactory(if (useJev) DecisionProviderSettings.typeSafe else DecisionProviderSettings.openAI)
```

  `asOpenAIDecisions` takes any decision provider (Liquid, Perplexity, OpenRouter, a local llama.cpp) or an existing
  service - one of the factory, also behind `TypeSafeServiceAdapters.retry`, keeps its own path (native on OpenAI's host);
  any other service is translated (`asOpenAIDecisions(service, imageInput)` to let images through). With no model in the settings
  each host uses its default. A predicate becomes a noul, a choice a choice (a boolean value as its text), a score a
  score; unnamed questions get keys of their own; the answers come back in the questions' order with their names,
  values and level labels, a declined question as a refusal; failures are the `OpenAIScala*` exceptions. What does not
  carry over to a System One host: the `safety_identifier` and image `detail` (dropped with a warning), a text and a
  boolean option of the same spelling in one question (refused), images for a host that reads none (refused). Live
  (2026-10-07, [DecisionApiSwitchSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/DecisionApiSwitchSmokeTest.scala)):
  the same `createDecision` call answered alike on OpenAI and Jev - refund 1.0 / 0.99, billing 1.0 / 1.0, urgency
  1.79 / 1.78.


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

**Typed decisions and re-ranking** are routines on top of a decision service, kept out of the service itself: import
`DecisionServiceExtra._` and they work on any `TypeSafeService` (Jev, d1, Perplexity's decider, OpenRouter's models).

**Typed decisions.** `decide[T]` asks about a whole case class in one call. Each field becomes a question:
- a `Boolean` is a yes/no question;
- an enum (a sealed trait of case objects, an `Enumeration`, a Java or Scala 3 enum) is a choice;
- an `Int` with `@JsonSchemaRange` of at most 10 values is a scale;
- a `Seq` of an enum is one yes/no question per option;
- a nested case class contributes its own fields.

`@JsonSchemaDescription` turns a field into its question, and on an enum's values it explains the options (a choice's
per-option criteria; for a `Seq` of an enum, each option's own question). The answers come back as a `T`, and `Decision[T]` keeps the
probabilities behind each field, keyed by the field's path. The schema is derived from the case class on Scala 2 and 3
alike (`JsonSchemaOf[T]`); a field nothing can answer, such as free text, is refused before the call.

```scala
  import io.cequence.openaiscala.domain.{JsonSchemaDescription, JsonSchemaRange}
  import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._

  sealed trait Team
  @JsonSchemaDescription("Payments, invoices, refunds") case object Billing extends Team
  @JsonSchemaDescription("Problems using the product") case object Technical extends Team
  @JsonSchemaDescription("Pricing, quotes, upgrades") case object Sales extends Team
  implicit val teamFormat: Format[Team] = JsonUtil.enumFormat[Team](Billing, Technical, Sales)

  case class Triage(
    @JsonSchemaDescription("Does the customer ask for a refund?") refund: Boolean,
    @JsonSchemaDescription("Which team should handle the ticket?") team: Team,
    @JsonSchemaDescription("How urgent is the ticket, from 1 (it can wait) to 5 (right now)?")
    @JsonSchemaRange(1, 5) urgency: Int
  )
  implicit val triageFormat: Format[Triage] = Json.format[Triage]

  typeSafe.decide[Triage]("I was charged twice - please refund the second charge!").map { decision =>
    decision.value                         // Triage(true, Billing, 4)
    decision.noul("refund").noul           // 0.99
    decision.choice("team").probabilities  // Map(Billing -> 1.0, Technical -> 0.0, Sales -> 0.0)
    decision.score("urgency").probabilities
  }
```

See [TypeSafeTypedDecision](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/TypeSafeTypedDecision.scala).

**Re-ranking.** `rerank(query, passages)` scores how much each passage helps answer the query, for example to re-rank
what a vector store retrieved before it goes into a prompt. It returns the passages best first, each with its
probability; `rerankBy(query, items)(_.text)` does the same for any items.
- **One question per passage:** the passage is quoted as data inside `<document>` tags (tags inside it are defused)
  and the query is the state.
- **Batching:** requests are batched by count and size (32 passages, ~48k characters), 4 run at once. Identical
  passages are asked once and blank ones not at all; long ones are cut.
- **Too long:** a request the host still refuses as too long is split in two, and the halves are asked one after
  the other.
- **Other failures:** any other failure fails the call; wrap the service in `TypeSafeServiceAdapters.retry` for
  transient ones.

`RerankSettings` sets `minScore`, `topK`, the model, the question and the limits.

```scala
  import io.cequence.openaiscala.typesafe.domain.RerankSettings
  import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._

  typeSafe
    .rerank("How do I reset my password?", retrievedPassages, RerankSettings(minScore = Some(0.5), topK = Some(5)))
    .map(_.map(ranked => (ranked.score, ranked.item)))  // [(0.96, "Password resets are done from ..."), ...]
```

In a live probe with ten passages, one of them an injected "answer yes", Jev scored the injection 0.03 and the two
relevant passages 0.95–0.96. Perplexity's decider gave it 0.16, still far below the relevant 0.99. Treat the scores like
the content itself, as untrusted. See
[TypeSafeRerank](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/TypeSafeRerank.scala).

**Answer helpers.** `ChoiceAnswer.probabilityOf(option)` and `margin` (how far the top option leads the runner-up; a
small margin is a close call), and `ScoreAnswer.probabilityAtLeast(level)` (e.g. "high or worse").

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

**Confidence fields** (🔥 New): a `number` property named `<field>_confidence` or `<field>Confidence` next to a field
`<field>` (at any depth) is not asked but filled with System One's confidence in that field's answer, rounded to 4 decimals -
a boolean's probability of the emitted answer, a choice's or score's `confidence`, the weakest option of a multi-select, the
minimum over everything under an object. Add `"is_urgent_confidence" -> JsonSchema.Number()` to the schema above and the
answer carries it right after `is_urgent`.

`TypeSafeChatMapping.toState(messages)` / `toQuestions(schema)` show what a call will send, and
`examples/typesafe/TypeSafeOpenAIAdapterWalkthrough` prints the exact System One request an OpenAI-shaped call turns into.
Questions are named by their property paths (`customer.is_angry`, a multi-select option as `topics.[payments]`, a `.` or
`\` inside a name escaped with a backslash), and the answers in `originalResponse` carry the same names.
Of the standard settings only `model`, `response_format_type`, `jsonSchema` and `n` = 1 are honoured; anything else you
set (temperature, `max_tokens`, `seed`, `reasoning_effort`, ...) is dropped with one warning naming it, since System One
does not sample. A free-form string in the schema, a plain (non-`json_schema`) request, `n > 1`, tools, streaming and
image content (read only by [Perplexity's decider](#perplexity-decisions-api-pplx-decider-)) are refused up front with an
explanation. The `jev-*` ids are listed under `models-supporting-json-schema`,
so the JSON helper keeps the request in schema mode, and a `chatCompletionRouter` can send closed-vocabulary schemas to
Jev and everything else to an LLM. See `examples/typesafe/TypeSafeCreateChatCompletionWithJSON`, and
`TypeSafeOpenAIAdapterScenarios` for fifteen "how to call it and what happens when" cases (JSON user messages, system
instructions, multi-turn, thresholds, `originalResponse`, dropped settings, and every refusal).
`TypeSafeSemanticFind` ports the [semantic search cookbook](https://docs.typesafe.ai/cookbooks/semantic_find) to the
adapter: the 218 tagged lines of GitHub's Terms of Service are the state, a string enum over the line ids ranks every
line by relevance and a boolean tells whether the document answers the query at all, in one ~200 ms request.

## Liquid AI (d1) 💧

[Liquid AI](https://www.liquid.ai/)'s first decision model **d1** (launched 2026-09-30) is served on the **same System One
API as TypeSafe's Jev** - the same `state` + typed `questions` in, calibrated probabilities out - so the TypeSafe lib
(`openai-scala-typesafe-client`) talks to it unchanged; only the host, the key and the model differ:

| | Status |
|---|---|
| Decisions - native `systemOne`, `listModels` (`TypeSafeServiceFactory.liquid()`) | ✅ works (`d1:free`, `d1`) |
| Decisions as structured output - the OpenAI adapter (`TypeSafeServiceFactory.liquidAsOpenAI()`, `json_schema` only) | ✅ works |
| Images - the paid `d1` (`d1:free` refuses them) | ✅ works, natively and through the OpenAI adapter |
| Open d1 - the open-weight d1-3B and d1-omni-600M on a local llama.cpp (`DecisionProviderSettings.llamaCpp`) | ⏳ the provider works (live with other decision models); llama.cpp b11476 cannot load d1 yet |
| Chat - Liquid's OpenAI-compatible surface (`ChatProviderSettings.liquid`, `https://api.liquid.ai/openai/v1/`) | ❌ answers, but lists no chat models for a free-tier key (the LFM chat models are on OpenRouter) |

```scala
  import io.cequence.openaiscala.typesafe.domain.{ChoiceQuestion, NoulQuestion}
  import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory

  val d1 = TypeSafeServiceFactory.liquid()   // LIQUID_API_KEY, https://api.liquid.ai/decisions, d1:free

  d1.systemOne(
    state = "I have been waiting over three weeks for my order and nobody answers my emails. I want my money back.",
    questions = Map(
      "is_complaint" -> NoulQuestion("Is this message a complaint from the customer?"),
      "department" -> ChoiceQuestion(
        "Which team should handle it?",
        "billing" -> "payments, refunds and invoices",
        "shipping" -> "deliveries and orders"
      )
    )
  ).map { response =>
    response.noul("is_complaint").noul     // 0.995
    response.choice("department").ranked   // List((billing, 0.73), (shipping, 0.27))
    response.usage                         // no output tokens are billed
  }

  // as an OpenAIChatCompletionService for json_schema structured output - the same adapter as Jev's
  val service = TypeSafeServiceFactory.liquidAsOpenAI()
```

**d1 vs Jev** (live 2026-09-30, d1's launch day, the free tier): the answers agree (complaint 0.995 / 0.990, department
billing 0.73 / 0.79, frustration 2.27 / 2.33 of 0..3) and d1 bills no output tokens (Jev: 73). Median latency per call on a
kept-alive connection:

| Questions per call | d1 | Jev |
|---|---|---|
| 1 | 356 ms | 236 ms |
| 3 | 346 ms | 244 ms |
| 10 | 559 ms | 239 ms |
| 20 | 853 ms | 243 ms |
| 40 | 1,517 ms | 273 ms |

d1 takes ~340 ms plus ~30 ms per question beyond three; Jev stays flat whatever the count. Under load d1 varies a lot: a
later run the same evening measured medians of 459 / 1,016 / 1,292 / 6,667 / 1,562 ms (outliers up to 16 s), Jev
unchanged at ~250 ms. The free tier (`d1:free`) was
intermittently unavailable on launch day - stretches of 429 `model_unavailable`, at times for ten minutes, and seconds-long
answers right after one - so pace your calls and wrap the service in `TypeSafeServiceAdapters.retry`, which treats a 429 as
transient.
Liquid's errors come in OpenAI's shape; they arrive as the same `TypeSafeScala*Exception`s, classified by status, with
Liquid's code (e.g. `model_unavailable`) in `errorType`. See
[LiquidD1SmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/LiquidD1SmokeTest.scala)
(the models, a native call, the OpenAI adapter, Jev side by side, and the latency benchmark).

**Images** (the paid `d1`, live 2026-10-07): put `DecisionImage` parts anywhere in the state, as for Perplexity - the
client lifts them into the top-level `images` array Liquid documents and leaves an `[image n]` marker where each stood
(Liquid itself lifts only the parts of an array state and reads one nested in an object as text: blue at 0.56 instead of
0.9999). Through `liquidAsOpenAI(defaultModel = TypeSafeModelId.liquid_d1)` the image content of user messages goes there
too. At most 8 images, 10,000 32 x 32 patches in all and an aspect ratio of 100 - each a quick 422 - as base64 PNG, JPEG
or WebP (a GIF is refused, despite the docs); an image costs 1.5 input tokens per patch for every question, at $0.04 / 1M
input tokens.

```scala
  val liquid = TypeSafeServiceFactory.liquid() // d1:free by default - text only

  // the model is a per-call choice: systemOne's optional third argument (as decide[T]'s `model`,
  // RerankSettings.model and CreateDecisionSettings.model); the paid d1 reads the photo
  liquid.systemOne(
    Json.obj("note" -> "A photo from a customer.", "photo" -> DecisionImage(Files.readAllBytes(photo))),
    Map("damaged" -> NoulQuestion("Is the product in the photo damaged?")),
    TypeSafeModelId.liquid_d1
  )

  // or make it the service's default: TypeSafeServiceFactory.liquid(defaultModel = TypeSafeModelId.liquid_d1)
```

**Open d1** (2026-10-07): Liquid released the weights - d1-3B (text and images) and the experimental d1-omni-600M (text and
images or audio) - with GGUF builds for llama.cpp's `/v1/systemone`. `DecisionProviderSettings.llamaCpp` serves them (no key,
`http://127.0.0.1:8080/`, the router-mode ids `TypeSafeModelId.liquid_d1_3b_gguf` / `liquid_d1_omni_600m_gguf`), but the
llama.cpp release of the same day (b11476) cannot load them yet ("unsupported decision model type: d1") - the provider was
verified live with the decision models llama.cpp does run (Julia-1, Laya). Audio is not supported here: no server takes it
yet (Liquid's API ignores an `audio` field). See
[LiquidD1ImagesSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/LiquidD1ImagesSmokeTest.scala)
and [LlamaCppDecisionsSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/LlamaCppDecisionsSmokeTest.scala).

## Perplexity Decisions API (pplx-decider) 🧭

Perplexity's open-sourced decision model **`pplx-decider-v1.1-27b`** (the 2026-10-06 update - Apache-2.0 weights on a
Qwen3.8-27B backbone, Decision Index 61.56 against the launch model `pplx-decider-v1-27b`'s 56.4; live 2026-10-08 the API
answers byte for byte alike under both ids) answers the same typed questions as
TypeSafe's Jev on Perplexity's **Decisions API** (`POST https://api.perplexity.ai/v1/decisions`) - and it is multimodal:
images can go into the `state`. The TypeSafe lib (`openai-scala-typesafe-client`) talks to it; only the host, the path, the
key (`PERPLEXITY_API_KEY`, else `SONAR_API_KEY`) and the model differ. Input costs $0.02 per million tokens (was $0.04 until
2026-10-08), output is free.

| | Status |
|---|---|
| Decisions - native `systemOne` (`TypeSafeServiceFactory.perplexity()`) | ✅ works |
| Images in the state - `DecisionImage(bytes)`, base64 PNG / JPEG / WebP data URLs, anywhere in the state | ✅ works (format checked before sending; no size cap - the API scales any image) |
| Decisions as structured output - the OpenAI adapter (`TypeSafeServiceFactory.perplexityAsOpenAI()`, `json_schema` only), images in user messages | ✅ works |
| `listModels` | returns the one model without a request (Perplexity's `/v1/models` lists its Agent API models) |

```scala
  import io.cequence.openaiscala.domain.{TextContent, UserSeqMessage, VLMContent}
  import io.cequence.openaiscala.typesafe.domain.{ChoiceQuestion, DecisionImage, NoulQuestion, ScoreQuestion, TypeSafeModelId}
  import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
  import play.api.libs.json.Json
  import java.nio.file.{Files, Paths}

  val decider = TypeSafeServiceFactory.perplexity()   // PERPLEXITY_API_KEY (or SONAR_API_KEY), pplx-decider-v1.1-27b

  decider.systemOne(
    state = Json.obj("review" -> "The headphones sound great, but the battery stopped charging after two weeks."),
    questions = Map(
      "defect" -> NoulQuestion("Does the review report a product defect?"),
      "sentiment" -> ChoiceQuestion(
        "What is the overall sentiment of the review?",
        "positive" -> "Mostly satisfied",
        "mixed" -> "Praise and complaints in one review",
        "negative" -> "Mostly dissatisfied"
      ),
      "severity" -> ScoreQuestion("How severe is the reported problem?", "Cosmetic", "Inconvenient", "Product unusable")
    )
  ).map { response =>
    response.noul("defect").noul          // 0.942
    response.choice("sentiment").choice   // mixed (0.95)
    response.score("severity").score      // 1.78 of 0..2
  }

  // an image: DecisionImage builds the part from PNG, JPEG or WebP bytes (or fromDataUrl) and refuses a bad
  // format or URL up front (no size cap - the API scales any image); the part may go anywhere in the state
  val square = Files.readAllBytes(Paths.get("square.png"))

  decider.systemOne(
    state = Json.arr("Which color is the square?", DecisionImage(square)),
    questions = Map("color" -> ChoiceQuestion.ofLabels("What color is the square?", "red", "blue", "green"))
  )

  // as an OpenAIChatCompletionService for json_schema structured output - the image parts of user messages go
  // into the state, e.g. via VLMContent (a "[file: NAME]" label + the image as a data URL)
  val service = TypeSafeServiceFactory.perplexityAsOpenAI()

  service.createChatCompletionWithJSON[Square](
    Seq(UserSeqMessage(TextContent("Which color is the square?") +: VLMContent.of(square, "square.png"))),
    CreateChatCompletionSettings(model = TypeSafeModelId.pplx_decider_v1_1_27b).withJsonSchema(squareSchema)
  )  // Square(blue, 0.99) - a `color` enum plus a `color_confidence` number
```

**Limits** (checked live 2026-10-02): 1 to 128 questions (checked before sending, for d1 too), 255 options per choice, 10 levels per score, an input under 262,144
tokens (a longer one is a `TypeSafeScalaTokenCountExceededException`), a body under 32 MiB, 10 requests per second per
organization. Jev and d1 stop at 10 score levels too, so the schema planner refuses a wider range or numeric enum up front.
Images go only as base64 PNG / JPEG / WebP data URLs - the API never fetches a URL - of any size: since 2026-10-06 the API
scales an image to about 2,100 input tokens (a 4032 x 3024 phone photo and an 8192 x 8192 image both answered in about a
second on 2026-10-08), where it used to time out (504) over 2,048 tiles of 32 x 32 pixels, so the client no longer caps the
size (a `DecisionProvider` with `maxImageTiles` still refuses larger images up front for a host that needs it). Input tokens
cost $0.02 per million (was $0.04). Jev reads an image part as text, so the plain `asOpenAI()` still refuses image content
(Liquid's paid `d1` takes images in an array of their own - see [Liquid AI (d1)](#liquid-ai-d1-)).

**pplx-decider vs Jev vs d1** (live 2026-10-02): the answers agree (defect 0.942 / 0.940 / 0.988, sentiment mixed 0.95 /
0.94 / 0.87, severity 1.78 / 1.99 / 1.88 of 0..2), and the blue square is blue at 0.99 (161 input tokens). On 2026-10-08, with
v1.1 behind both ids, the decider said defect 0.996, mixed 0.774, severity 1.94 (Jev 0.940 / 0.920 / 1.99, d1 0.977 / 0.877 /
1.89). Median latency per call on a kept-alive connection:

| Questions per call | pplx-decider | Jev | d1 (free tier) |
|---|---|---|---|
| 1 | 213 ms | 243 ms | 837 ms |
| 3 | 210 ms | 246 ms | 295 ms |
| 10 | 277 ms | 244 ms | 584 ms |
| 20 | 335 ms | 261 ms | 739 ms |
| 40 | 457 ms | 274 ms | 732 ms |

The decider takes ~210 ms plus ~7 ms per question beyond three - the fastest of the three for a few questions; Jev stays
flat, d1's free tier varies from run to run. One earlier run had ~1.1 s medians at one and three questions, a slow stretch on
Perplexity's side. A rerun on 2026-10-08 (v1.1): 282 / 264 / 369 / 528 / 862 ms at 1 / 3 / 10 / 20 / 40 questions (~15 ms per
question this time), Jev 240-259 ms flat, d1's free tier 229-378 ms with a few failed calls. Errors come in OpenAI's shape and arrive as the same `TypeSafeScala*Exception`s, classified by status,
with `error.type` in `errorType` and the `x-request-id` as the request id. See
[PerplexityDeciderSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/PerplexityDeciderSmokeTest.scala)
(a native call on both ids, an image, a 4032 x 3024 photo, the OpenAI adapter with and without an image, Jev and d1 side by
side, and the latency benchmark).

## Decision-model providers 🧩

Decision models are spreading fast, and most hosts copy TypeSafe's System One protocol - so the TypeSafe lib reaches them all
through **`DecisionProviderSettings`**, the decision-model counterpart of `ChatProviderSettings`. OpenAI's Decisions API has
a protocol of its own; its preset translates the questions and answers, so every routine on a decision service runs on it:

```scala
  import io.cequence.openaiscala.typesafe.domain.{DecisionProvider, TypeSafeModelId}
  import io.cequence.openaiscala.typesafe.service.{DecisionProviderSettings, TypeSafeServiceFactory}

  val openRouter = TypeSafeServiceFactory(DecisionProviderSettings.openRouter)   // OPENROUTER_API_KEY
  openRouter.listModels                                                         // its ten decision models
  openRouter.systemOne(state, questions, TypeSafeModelId.openrouter_liquid_d1)

  // behind the OpenAI interface (json_schema structured output); image content goes into the state where the host reads it
  val decider = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.perplexity)

  // OpenAI's Decisions API (gpt-6-luna) - its own protocol, translated; OPENAI_SCALA_CLIENT_API_KEY
  val luna = TypeSafeServiceFactory(DecisionProviderSettings.openAI)
  luna.decide[Triage](ticket)

  // any other host that speaks the protocol
  val upstage = TypeSafeServiceFactory(DecisionProvider("https://api.upstage.ai/", "UPSTAGE_API_KEY", "solar-decide"))
```

| Preset | Key | Models | Notes |
|---|---|---|---|
| `typeSafe` | `TYPESAFE_API_KEY` | `jev-latest` | the default of `TypeSafeServiceFactory()` |
| `liquid` | `LIQUID_API_KEY` | `d1:free`, `d1` | at most 128 questions; the paid `d1` reads images (sent in a top-level `images` array) |
| `perplexity` | `PERPLEXITY_API_KEY` (or `SONAR_API_KEY`) | `pplx-decider-v1.1-27b`, `pplx-decider-v1-27b` | `POST /v1/decisions`, images of any size, at most 128 questions |
| `openRouter` | `OPENROUTER_API_KEY` | Jev, d1, Perplexity's deciders, OpenAI's Luna, Cloudflare's Clef (+ Flash), Solar Decide (+ Flash), Mercury Decide (+ free), Tev1, Kev 4B, Span-01 (+ Lite) | listed with `output_modalities=decisions`; Span-01 takes noul questions only |
| `openAI` | `OPENAI_SCALA_CLIENT_API_KEY` (or `OPENAI_API_KEY`) | `gpt-6-luna` | OpenAI's own protocol (translated), images, at most 200 questions; a refusal arrives as `UnknownAnswer("refusal", ...)` |
| `llamaCpp` | none (`LLAMA_API_KEY` if the server has one) | whatever `llama-server` loaded | a local server, `http://127.0.0.1:8080/`; a router needs a model id from `listModels` (its decision models only); images in a top-level `images` array, for a model with a projector (else a 501) |

A `DecisionProvider` carries what differs between hosts: the protocol (System One, or OpenAI's), the base URL, key
variable and default model, the decisions path (`v1/systemone`, `v1/decisions` on Perplexity and OpenAI), how `listModels` finds the models (TypeSafe's `{"models"}` list, an OpenAI-style
`{"data"}` list with query parameters, or a fixed list - with what each model reads, when the host says), a question cap and
whether and how the host reads images (in the state, or lifted into a top-level `images` array; both checked before sending,
with the largest image a host takes), the request id headers, fallback key variables and whether a key is needed at all. `forProvider(provider, apiKey, timeouts)` and
`withEngine(engine, provider)` take an explicit key or a shared engine. OpenRouter's ids are in `models-supporting-json-schema`;
`createChatCompletionWithJSON` works with another host's ids too: it then sends the schema in the prompt (JSON-object mode),
and the adapter reads it back from there.

**OpenRouter's decision models** (live 2026-10-02, `examples/typesafe/OpenRouterDecisionsSmokeTest`): the docs' review example,
and the median latency per call by the number of questions:

| Model | defect | sentiment | severity (0..2) | 1 question | 10 | 40 |
|---|---|---|---|---|---|---|
| `~typesafe/jev-latest` | 0.940 | mixed 0.93 | 1.99 | 244 ms | 243 ms | 283 ms |
| `liquid/d1` | 0.976 | mixed 0.78 | 1.86 | 358 ms | 336 ms | 460 ms |
| `inception/mercury-decide:free` | 0.989 | mixed 0.96 | 1.93 | 517 ms | 642 ms | 966 ms |
| `togethercomputer/tev1-4b-experimental` | 0.755 | mixed 0.93 | 1.62 | 274 ms | 506 ms | 1,723 ms |
| `jaredpalmer/kev-4b` | 0.703 | mixed 0.75 | 1.46 | 451 ms | 659 ms | 795 ms |
| `upstage/solar-decide` | 0.981 | mixed 0.99 | 1.06 | 985 ms | 10.6 s | 46.9 s |

Respan's Span-01 (and its Lite / free variants) judges with noul questions only, over a text state or a conversation trace
(`{"input": [messages], "output": message}`) - defect 0.965 on the same review, and 0.016 for "is the summary faithful" on a
trace whose answer misreports it. Through OpenRouter the response names the dated build (`liquid/d1-20260930`), the request
id is OpenRouter's `x-generation-id`, and an upstream refusal comes back wrapped (`HTTP 422: {...}`).

Other hosts that speak the protocol, from their docs (not live-verified here - build a `DecisionProvider`): Upstage
(`https://api.upstage.ai/`, `solar-decide`), Vercel's AI Gateway (`https://ai-gateway.vercel.sh/typesafe/`, Jev / d1 / Laya),
meraGPT, milliseconds.ai, SiliconFlow (`Kev-4B`), Berget, Opper, Featherless, and local runtimes besides llama.cpp such
as Ollama 0.35+ (`http://localhost:11434/`). OpenAI's and xAI's `/v1/decisions` exist but are gated (a limited preview / a per-key
permission).
