# Decision models 🎯

[← back to the README](../README.md)

Decision models give typed answers - yes/no probabilities, choices, scores - with calibrated probabilities, in a few
hundred milliseconds. OpenAI's Decisions API is part of the full `OpenAIService`; TypeSafe AI's Jev, Liquid AI's d1,
Perplexity's decider, Microsoft-Decision-1, SubQ Snap, ScaleDown's Classify, OpenRouter's catalogue, Vercel AI Gateway and a local llama.cpp are served through the `typesafe-client` module,
which also serves either API on either host, typed decisions (`decide[T]`) and re-ranking.

## OpenAI Decisions API (gpt-6-luna) ⚖️

- **Decisions API** (public beta) - typed answers about text and inline images, about 10x faster than asking a
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
grows with the number of questions). Model names come from `typeSafe.listModels`, or as constants from `DecisionModelId`
(every decision model the module reaches, `jev_latest` to `vercel_laya_free`; `TypeSafeModelId`, its 1.4.0 name, still
compiles as a deprecated alias of it); `TypeSafeServiceFactory.withEngine(engine)`
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
  import io.cequence.openaiscala.typesafe.domain.{SystemOneResponse, DecisionModelId}
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
    CreateChatCompletionSettings(model = DecisionModelId.jev_latest).withJsonSchema(triageSchema)
  ).map { case (triage, response) =>
    triage                                                        // Triage(technical, true, 4, List(payments, integration))
    response.originalResponse.collect { case r: SystemOneResponse =>
      r.choice("department").ranked                               // List((technical, 0.98), (billing, 0.02), (sales, 0.0))
    }
  }
```

**Confidence fields**: a `number` property named `<field>_confidence` or `<field>Confidence` next to a field
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
image content on a host that reads no images are refused up front with an explanation. The `jev-*` ids are listed under `models-supporting-json-schema`,
so the JSON helper keeps the request in schema mode, and a `chatCompletionRouter` can send closed-vocabulary schemas to
Jev and everything else to an LLM. See `examples/typesafe/TypeSafeCreateChatCompletionWithJSON`, and
`TypeSafeOpenAIAdapterScenarios` for fifteen "how to call it and what happens when" cases (JSON user messages, system
instructions, multi-turn, thresholds, `originalResponse`, dropped settings, and every refusal).
`TypeSafeSemanticFind` ports the [semantic search cookbook](https://docs.typesafe.ai/cookbooks/semantic_find) to the
adapter: the 218 tagged lines of GitHub's Terms of Service are the state, a string enum over the line ids ranks every
line by relevance and a boolean tells whether the document answers the query at all, in one ~200 ms request.

### Jev 1.14, images, boxes and long context 🧪

🔥 TypeSafe's "JevDay" release (2026-10-14) brought **`jev-1.14.0`** (`jev-preview` now, `jev-latest` about a week later -
small improvements everywhere, most in multilingual support; `jev-1.13.0` stays available) and three "danger zone"
features - dreamer-ready, not yet production-grade in TypeSafe's words, each behind an opt-in header:

- **Images** (Jev 1.13 and 1.14): put a `DecisionImage` anywhere in the state, in a question's instructions (a reference
  picture) or in its criteria (options defined by example pictures). The client sends each as one of TypeSafe's
  **assets** - a placeholder (a UUID) where the image was, the image once in a top-level `assets` map - and opts in with
  `TypeSafe-Danger-Zone-Images: true` itself (`DecisionImages.Assets`, the `typeSafe` preset). Jev takes PNG or JPEG of
  256 to 1,568 pixels a side (78,400 to 2,458,624 in all) and never resizes, so anything else is refused before sending;
  a 512 x 512 image costs ~750 input tokens, again for every place it is used.
- **Boxes** (Jev 1.14 only): `BoxQuestion("the red rectangle in `photo`")` answers where something is - a `BoxAnswer`
  with a `BoundingBox` (top / bottom / left / right as fractions of the image, `inPixels(w, h)`, `padded(margin)`) and a
  confidence. There is no "not found": an absent target still gets a box, with a low confidence - ask a noul too. Only
  TypeSafe answers boxes (`DecisionProvider.boxQuestions`, set on the `typeSafe` presets), so a box question for any
  other provider - natively, a `BoundingBox` field of `decide[T]` or of the JSON schema adapter - is refused before
  sending ("liquid answers no box questions (mug) - ..."); a provider of your own whose host answers them sets
  `boxQuestions = true`. Jev before 1.14 (`jev-latest` until it moves on) answers one with a bare 400 "Invalid
  request.", to which the client adds why ("box questions need Jev 1.14 or later (e.g. jev-1.14.0)").
- **Long context**: `DecisionProviderSettings.typeSafeLongContext` sends `TypeSafe-Danger-Zone-Long-Context: true` with
  every request - 262k tokens per request and per question instead of 64k / 32k, at the same price, slower and less
  accurate past the defaults. `DecisionProvider.extraHeaders` carries it (or any header of your own).

```scala
  val jev = TypeSafeServiceFactory(DecisionProviderSettings.typeSafe)

  jev.systemOne(
    Json.obj("photo" -> DecisionImage(Files.readAllBytes(kitchen))),
    Map(
      "mug"     -> BoxQuestion("the coffee mug in `photo`"),
      "has_mug" -> NoulQuestion("Is there a coffee mug in `photo`?")
    ),
    DecisionModelId.jev_1_14_0
  ).map { r =>
    if (r.noul("has_mug").noul > 0.5) crop(kitchen, r.box("mug").box.padded(0.05).inPixels(width, height))
  }

  // a reference picture in a question, options defined by example pictures
  NoulQuestion(Some(Json.arr("Is the person in `door` the same as in this badge photo:", DecisionImage(badge))), None)
  ChoiceQuestion(ListMap("thumbs_up" -> Some(DecisionImage(thumb)), "none" -> Some(JsString("No gesture"))), Some(JsString("Which gesture is in `camera`?")))

  val longJev = TypeSafeServiceFactory(DecisionProviderSettings.typeSafeLongContext)   // 262k tokens
```

The adapters take all of it. **JSON schema** (`asOpenAI()`, `decide[T]`): a chat message's image content goes into the
state, and an object of exactly the four `number` fields `top` / `bottom` / `left` / `right`, each a fraction of the
image (`minimum` 0, `maximum` 1) - a `BoundingBox` field, or a case class of your own with `@JsonSchemaRange(0, 1)` on its
four `Double` edges - is asked as a box question, its description the instructions, filled with the edges (a
`<field>_confidence` sibling gets the box's confidence); the same case class works with an LLM. The range is the
opt-in: four free numbers with those names (margins, insets) are refused like any free number, and with another range
or an enum they are score questions. **OpenAI's
Decisions interface** (`TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.typeSafe)`): an
`input_image` of a `createDecision` call reaches Jev as an asset; through `typeSafeLongContext` the call gets 262k tokens
(OpenAI's questions have no box type, so boxes stay System One's).

```scala
  case class Detection(
    @JsonSchemaDescription("the red rectangle in the image") red: BoundingBox,
    @JsonSchemaDescription("Is there a red rectangle in the image?") visible: Boolean
  )
  implicit val detectionFormat: Format[Detection] = Json.format[Detection]   // BoundingBox brings its own Format

  TypeSafeServiceFactory.asOpenAI().createChatCompletionWithJSON[Detection](
    Seq(UserSeqMessage(Seq(TextContent("A photo."), ImageURLContent(dataUrl)))),
    CreateChatCompletionSettings(DecisionModelId.jev_1_14_0)
  )                                                          // Detection(BoundingBox(0.49, 0.81, 0.26, 0.5), true)
  jev.decide[Detection](Json.obj("photo" -> DecisionImage(bytes)), DecisionModelId.jev_1_14_0)
```

Live 2026-10-14 (`examples/typesafe/JevDangerZoneSmokeTest`, 16 sections, all passed): a red
circle read red at 0.99-1.00 and a circle at 1.00 on both builds (~1,100 tokens), the reference picture told the circle
(1.00) from the square (0.01), the example pictures picked the green triangle (1.00), boxes on 1.14 came within 0.02 of
both shapes' edges (confidence 0.73-0.81) while an absent shape's box had 0.05 (its noul 0.01), a box on Jev 1.13 is a 400
"Invalid request.", `decide[Shape]` and the OpenAI interface read an image right, a `BoundingBox` field came back as
(0.49, 0.81, 0.26, 0.50) for the rectangle at (0.5, 0.8, 0.25, 0.5) through both the JSON schema adapter and `decide[T]`,
`createDecision` with an `input_image` read the circle (red 0.96) and with long context answered over 57k tokens,
~0.2-0.45 s per call. The limits by
Jev's own count (`usage.input_tokens`): **33,024** input tokens for a request of one question and ~65.8k for a request
(state plus every question) by default, **262,400** for both with long context (120k tokens answered in 1.7 s, 250k in
4.6 s) - one more is a 400 `max_tokens_exceeded`. Without the opt-in an image or a box is a 400 "Images are experimental.
Send the header TypeSafe-Danger-Zone-Images: true to use them."; a WebP, an unused asset or an image out of bounds a 400
"Invalid image request." / "Invalid image dimensions."; an image left as an `image_url` part is read as text. OpenRouter's
Jev drops `assets` and refuses a box question (its validator knows only noul / choice / score), and does not list Jev 1.14
yet - so these features are TypeSafe's own host only. The data opt-in ("Help improve TypeSafe models") is an organization
setting in TypeSafe's console, not an API flag.

## Liquid AI (d1) 💧

[Liquid AI](https://www.liquid.ai/)'s decision models - **d1** (launched 2026-09-30) and the stronger **d1-ultra**
(2026-10-15) - are served on the **same System One API as TypeSafe's Jev** - the same `state` + typed `questions` in,
calibrated probabilities out - so the TypeSafe lib (`openai-scala-typesafe-client`) talks to them unchanged; only the host,
the key and the model differ:

| | Status |
|---|---|
| Decisions - native `systemOne`, `listModels` (`TypeSafeServiceFactory.liquid()`) | ✅ works (`d1` by default, `d1-ultra`; the free tier `d1:free` was removed on 2026-10-14) |
| Decisions as structured output - the OpenAI adapter (`TypeSafeServiceFactory.liquidAsOpenAI()`, `json_schema` only) | ✅ works |
| Images - `d1` and `d1-ultra` | ✅ works, natively and through both OpenAI adapters |
| Open d1 - the open-weight d1-3B and d1-omni-600M on a local llama.cpp (`DecisionProviderSettings.llamaCpp`) | ⏳ the provider works (live with other decision models); llama.cpp b11476 cannot load d1 yet |
| Chat - Liquid's OpenAI-compatible surface (`ChatProviderSettings.liquid`, `https://api.liquid.ai/openai/v1/`) | ❌ answers, but lists no chat models for a free-tier key (the LFM chat models are on OpenRouter) |

```scala
  import io.cequence.openaiscala.typesafe.domain.{ChoiceQuestion, NoulQuestion}
  import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory

  val d1 = TypeSafeServiceFactory.liquid()   // LIQUID_API_KEY, https://api.liquid.ai/decisions, d1 by default

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
unchanged at ~250 ms. The free tier (`d1:free`, removed on 2026-10-14) was
intermittently unavailable on launch day - stretches of 429 `model_unavailable`, at times for ten minutes, and seconds-long
answers right after one - so pace your calls and wrap the service in `TypeSafeServiceAdapters.retry`, which treats a 429 as
transient.
Liquid's errors come in OpenAI's shape; they arrive as the same `TypeSafeScala*Exception`s, classified by status, with
Liquid's code (e.g. `model_unavailable`) in `errorType`. See
[LiquidD1SmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/LiquidD1SmokeTest.scala)
(the models, a native call, the OpenAI adapter, Jev side by side, and the latency benchmark).

🔥 **d1-ultra** (2026-10-15, [launch post](https://www.liquid.ai/blog/d1-ultra)): Liquid's strongest decision model - a
larger open-weight base with Liquid's training, 44 languages, 65 on Artificial Analysis' Decision Index, better accuracy
and calibration than d1 and Jev 1.14 across Liquid's 112 evaluations - on the same API, with images the same way
(`DecisionModelId.liquid_d1_ultra`, in `models-supporting-json-schema`). It costs $0.042 per million input tokens, no
output; like d1, every question is billed as its own prompt - the state, its images and the question - so 40 questions
over a short review bill 3,007 input tokens (Jev ~1,400, reading the state once). Live 2026-10-15
(`LiquidD1SmokeTest model=d1-ultra`, `DecisionModelsLatencyBenchmark only=liquid`, `DecisionContextLatencyBenchmark
only=liquid`; median per call):

| | d1-ultra | d1 | Jev 1.13 |
|---|---|---|---|
| 1 / 10 / 40 questions | 208 / 227 / 237 ms | 254 / 250 / 318 ms | 210 / 217 / 227 ms |
| a 1k / 8k / 16k-token state | 230 / 639 / 599 ms | 207 / 283 / 516 ms | |
| a question's prompt (the state, its images and the question) | at most 32,768 tokens | at most 65,536 tokens | 33,024 tokens (65.8k per request) |
| input per 1M tokens | $0.042 | $0.02 (was $0.04 on 2026-10-07) | $0.042 |

d1-ultra stays flat like Jev where d1 grows with the question count. The answers agree with d1 and Jev (the triage above:
complaint 0.997, frustration 2.27 of 0..3 - d1 0.998 / 2.22, Jev 0.990 / 2.34 - though d1-ultra puts the refund request
in shipping, 0.57 to billing's 0.43, where d1 and Jev say billing at 0.72 / 0.76); a red circle reads red 1.00 / blue 0.00
/ circle 1.00 through both OpenAI adapters; a defect report in English, German, Slovak, Japanese, Arabic and Swahili
scores 0.95-0.98 (d1 the same, but Slovak 0.92 to 0.97), the no-defect controls ~0. Limits as d1's: 128 questions, 10
score levels, 8 images / 10,000 patches, the state under 1 MB, a prompt under the token limit or 250,000 bytes of text -
over it a 422 "The prompt for question `x` is over the model's input limit of 32768 tokens, images included, or 250000
bytes of text.", a `TypeSafeScalaTokenCountExceededException` (as d1's at 65,536); the test sentence of the context
benchmark counts 20 tokens there, so 32,768 is about 31k tokens of Jev's count.

**Images** (`d1` and `d1-ultra`, live 2026-10-07 / 2026-10-15): put `DecisionImage` parts anywhere in the state, as for Perplexity - the
client lifts them into the top-level `images` array Liquid documents and leaves an `[image n]` marker where each stood
(Liquid itself lifts only the parts of an array state and reads one nested in an object as text: blue at 0.56 instead of
0.9999). Through `liquidAsOpenAI(defaultModel = DecisionModelId.liquid_d1)` the image content of user messages goes there
too. At most 8 images, 10,000 32 x 32 patches in all and an aspect ratio of 100 - each a quick 422 - as base64 PNG, JPEG
or WebP (a GIF is refused, despite the docs); an image costs 1.5 input tokens per patch for every question.

```scala
  val liquid = TypeSafeServiceFactory.liquid() // d1 by default

  // the model is a per-call choice: systemOne's optional third argument (as decide[T]'s `model`,
  // RerankSettings.model and CreateDecisionSettings.model)
  liquid.systemOne(
    Json.obj("note" -> "A photo from a customer.", "photo" -> DecisionImage(Files.readAllBytes(photo))),
    Map("damaged" -> NoulQuestion("Is the product in the photo damaged?")),
    DecisionModelId.liquid_d1_ultra
  )

  // or make it the service's default: TypeSafeServiceFactory.liquid(defaultModel = DecisionModelId.liquid_d1_ultra)
```

**Open d1** (2026-10-07): Liquid released the weights - d1-3B (text and images) and the experimental d1-omni-600M (text and
images or audio) - with GGUF builds for llama.cpp's `/v1/systemone`. `DecisionProviderSettings.llamaCpp` serves them (no key,
`http://127.0.0.1:8080/`, the router-mode ids `DecisionModelId.liquid_d1_3b_gguf` / `liquid_d1_omni_600m_gguf`), but the
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
  import io.cequence.openaiscala.typesafe.domain.{ChoiceQuestion, DecisionImage, NoulQuestion, ScoreQuestion, DecisionModelId}
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
    CreateChatCompletionSettings(model = DecisionModelId.pplx_decider_v1_1_27b).withJsonSchema(squareSchema)
  )  // Square(blue, 0.99) - a `color` enum plus a `color_confidence` number
```

**Limits** (checked live 2026-10-02): 1 to 128 questions (checked before sending, for d1 too), 255 options per choice, 10 levels per score, an input under 262,144
tokens (a longer one is a `TypeSafeScalaTokenCountExceededException`), a body under 32 MiB, 10 requests per second per
organization. Jev and d1 stop at 10 score levels too, so the schema planner refuses a wider range or numeric enum up front.
Images go only as base64 PNG / JPEG / WebP data URLs - the API never fetches a URL - of any size: since 2026-10-06 the API
scales an image to about 2,100 input tokens (a 4032 x 3024 phone photo and an 8192 x 8192 image both answered in about a
second on 2026-10-08), where it used to time out (504) over 2,048 tiles of 32 x 32 pixels, so the client no longer caps the
size (a `DecisionProvider` with `maxImageTiles` still refuses larger images up front for a host that needs it). Input tokens
cost $0.02 per million (was $0.04). Jev reads images too since Jev 1.14, as assets - see
[Jev 1.14, images, boxes and long context](#jev-114-images-boxes-and-long-context-) - and Liquid's paid `d1` takes them in
an array of their own (see [Liquid AI (d1)](#liquid-ai-d1-)).

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

## Microsoft-Decision-1 (Microsoft Foundry) 🪟

Microsoft's decision model **Microsoft-Decision-1** (public preview since 2026-10-09; built on Qwen3.5-9B, future
iterations to be rebased on OpenAI and MAI models; $0.042 per 1M input tokens in the US and EU data zones, output free) is
served from your own Microsoft Foundry resource and speaks TypeSafe's System One protocol - live-verified 2026-10-09 at
`POST <endpoint>/providers/microsoft/v1/systemone` with a `Bearer` key (the launch example's `<endpoint>/v1/systemone` is a
404 on the resource itself). So it is a `DecisionProvider` like the others - `TypeSafeServiceFactory.microsoftFoundry()`
(the resource endpoint `https://<resource>.services.ai.azure.com` from `FOUNDRY_BASE_URL`, the key from `FOUNDRY_API_KEY`,
the deployment from `FOUNDRY_MODEL` - the launch example's names), `microsoftFoundryWithEngine`,
`microsoftFoundryAsOpenAI()` for `json_schema` structured output, and `DecisionProviderSettings.microsoftFoundry(endpoint,
deployment)` for `decide[T]`, re-ranking and the guardrails. Microsoft positions it for classification, routing, evaluation
and workflow control, reporting quality competitive with GPT-5 at a fraction of the latency and cost (Xbox Research
categorised 10,000 feedback items with it).

**A request names your deployment, not the model.** Deploy the model in a Foundry resource (catalog → Deploy; Foundry
proposes the deployment name `Microsoft-Decision-1`, which Azure's resource-name rule refuses - `ContainsReservedWord` -
so give it a name of your own, `decision-1` by default here) and send that name as the model: `Microsoft-Decision-1` as
the model is a 404 `DeploymentNotFound`, while the response reports `model: microsoft-decision-1`. `listModels` lists the
resource's deployments of the model (Azure's `GET /openai/deployments`), so the ids to send are one call away. Since
2026-10-09 OpenRouter serves the same model as `microsoft/microsoft-decision-1` ($0.042 per 1M input tokens, 32k
context) - no deployment needed there.

```scala
  val decision1 = TypeSafeServiceFactory.microsoftFoundry() // FOUNDRY_BASE_URL + FOUNDRY_API_KEY (+ FOUNDRY_MODEL)

  decision1.listModels // Seq(ModelMetadata("decision-1", "Microsoft-Decision-1 deployment", "2026-10-09"))

  decision1.systemOne(
    "I was charged twice.",
    Map("team" -> ChoiceQuestion(
      "Which team should handle this customer support request?",
      "billing" -> "Charges, invoices, refunds, or subscription payments",
      "technical" -> "Software errors, bugs, or integration failures",
      "account" -> "Sign-in, password, or account-access problems"
    ))
  ) // billing at 0.998, ~250 ms, 67 input tokens
```

Live facts (2026-10-09, Sweden Central): the three launch-example requests route right at 0.997+ confidence in 0.2-0.7 s;
128 noul questions are answered in 0.6 s and a 30k-token state in 0.9 s (`input_tokens` ~20 per noul: the state is read
once, like Jev). Limits, all enforced by the host: at most 255 questions (a 422 for 256 - the client refuses more before
I/O), 2-255 options per choice and 2-10 score levels (one option or level is a 400), the state plus all questions under
64,000 tokens (a 422 arriving as `TypeSafeScalaTokenCountExceededException`, so re-ranking splits on it), the body under
1 MiB (a 413). A noul needs no instructions on Foundry (the client still requires instructions or criteria); score answers
carry a `legend`. No images: an `images` field is a 400 and an image part in the state is read as text, so the OpenAI
adapter maps none. Errors come as Azure's `{"error": {"code", "message", "details"}}` (400 `unsupported_request_argument` /
`unrecognized_request_argument` - an unknown top-level field is refused -, 404 `DeploymentNotFound`, 401) or the backend's
`{"detail": "invalid TypeSafe request: ..."}` 422s, classified as on the other hosts with the code in `errorType`; the
request id is Azure's `apim-request-id` (an `x-request-id` comes too), and the deployment's rate limit (50 requests per
minute by default) rides in `x-ratelimit-limit-requests` - 58 back-to-back calls provoked no 429. The `api-key` header
works as well as `Bearer`, and `?api-version` is not needed. `examples/typesafe/MicrosoftDecision1SmokeTest` walks all of
this (the deployments, routing, a noul and a score, an object state, `decide[Triage]`, the OpenAI interface, 128 questions,
the token limit) - all 10 sections passed.

## Vercel AI Gateway ▲

Vercel's AI Gateway serves decision models through a TypeSafe-compatible API, so `DecisionProviderSettings.vercel` reaches
them like any other host: `POST https://ai-gateway.vercel.sh/typesafe/v1/systemone` with an AI Gateway key (`vck_…`, created
under the team's AI Gateway → API keys, in `AI_GATEWAY_API_KEY`; a Vercel account token is a 401), the models listed at
`GET /typesafe/v1/models` in TypeSafe's shape - `typesafe-ai/jev` (the bare `jev` too), `liquid/d1` (text and images),
`microsoft/microsoft-decision-1`, `openai/gpt-6-luna-decisions` and Convai Innovations' `convaiinnovations/laya` /
`laya-free` - and billed through the gateway, which reports the routing, the cost and a `generationId` in the response's
`provider_metadata.gateway` (the request id is the `x-vercel-id` header). Images go as `image_url` parts in the state
array, as the gateway's AI SDK sends them.

```scala
  val gateway = TypeSafeServiceFactory(DecisionProviderSettings.vercel) // AI_GATEWAY_API_KEY

  gateway.listModels                                                     // the six decision models
  gateway.systemOne(review, questions, DecisionModelId.vercel_laya_free) // Laya, free tier
  gateway.systemOne(review, questions, DecisionModelId.vercel_jev)       // Jev, with paid credits
```

Live 2026-10-10 on a free-tier team (a card on file unlocks the free credits, which cover Laya only): Laya answered the
review in 0.5-1.4 s at the free tier's 5 requests per minute per region (a 429 `rate_limit_exceeded`, "Retry after
46s"), the other models answer a 403 `no_providers_available` ("Free tier users do not have access to this model") until
credits are bought - so Jev, d1 and its images, Microsoft-Decision-1 and Luna are not verified through the gateway here.
The gateway validates before routing, in a flat `{"message", "error_type": "invalid_request"}` shape: 2-10 score levels,
at most 255 options, a string / object / array state; an unknown model is a 404 `model_not_found`; an unknown top-level
field is ignored. A model's own limits come back as a 422 / 413 `AI_APICallError`: Laya (8k context) answered 40 questions
but not 200 nor a 20k-token state ("That request is too large for this model", a `TypeSafeScalaTokenCountExceededException`,
so re-ranking splits on it), and refused a noul without instructions. Its confidence-based
decision fallbacks (`providerOptions.gateway.models`: rerun an uncertain decision with another model) are the gateway's
own extension, not sent by this client. `examples/typesafe/VercelAIGatewaySmokeTest` walks the listing, the review on
every model (the paid ones reported as skipped on a free tier), a blue square on d1, the OpenAI interface and the error
cases, paced to the free tier.

## SubQ Snap 📜

🔥 SubQ Snap (`subq-snap-preview`, Subquadratic, public preview since 2026-10-12) is a long-context decision model on
TypeSafe's System One protocol: `POST https://api.subq.ai/v1/systemone` with a `Bearer` key in `SUBQ_API_KEY`, $0.05 per
1M input tokens, output free. `DecisionProviderSettings.subq` reaches it like Jev - every routine runs on it (`decide[T]`,
re-ranking, the guardrails, the OpenAI interface):

```scala
  val snap = TypeSafeServiceFactory(DecisionProviderSettings.subq)                 // SUBQ_API_KEY
  snap.systemOne(longContract, questions)                                         // up to 295,957 input tokens
  snap.systemOne(Json.arr("The number row stopped working.", DecisionImage(photo)), questions) // up to 4 images

  val asOpenAI = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.subq)    // image content of user messages too
```

Images go as `{"mediaType", "base64"}` objects in a top-level `images` array - the bare base64, no `data:` prefix
(`DecisionImages.ImageObjects`): the client lifts the state's image parts into it, leaving `[image n]` markers. SubQ
refuses a data URL string there ("images/0: must be object") and reads an `image_url` part left in the state as text.
At most 4 images, 4 MiB each and 8 MiB in all, PNG / JPEG / WebP.

Live 2026-10-12 (its launch day): the largest input it takes is **295,957 tokens** - about 77% of the launch post's 384K
- and one token more is a 400 "Input exceeds the token limit." (a `TypeSafeScalaTokenCountExceededException`). The
count is SubQ's own, from `usage.input_tokens` (its tokenizer, question included): a test sentence it counts as exactly
19 tokens, plus 87 for one question, matched every answered request from 1 to 15,572 sentences to the token, and the
limit was bisected to one token - 295,957 answered, one more word refused. ~1.4 s at the limit, ~0.3 s for 10k-32k
tokens, ~0.7 s for 95k on a kept-alive connection. Its errors are TypeSafe's `{"detail":
{"error_type", "message"}}`: a 422 for an unknown model (its chat model `subq-preview` included), a choice of one
option, more than 10 score levels, a noul without instructions or a question name over 100 characters; 402 is out of
credits (a `TypeSafeScalaApiUsageException`). No question cap (513 questions took 1.3 s), no request id header, an unknown
top-level field ignored. Its `GET /v1/models` lists the early-access chat model next to it for a key that has one, so
the preset's list is fixed. `examples/typesafe/SubQSnapSmokeTest` walks SubQ's docs example (a nurse-escalation noul, the
owning team, a score with its legend), an object state, `decide[Triage]`, the OpenAI interface with the schema inferred
from a case class, a blue square both ways, a ~95k-token state, the token limit and two refusals - all 11 sections
passed on Scala 2.13 and 3.

## ScaleDown Classify 🧮

🔥 ScaleDown's Constrained Decoding Models (launched 2026-10-13) answer one task each without a generative loop, and
**Classify** answers System One's questions - nouls, choices and scores - at `POST https://api.scaledown.xyz/v1/scaledown`
(the key in an `x-api-key` header, `SCALEDOWN_API_KEY`; models `classify-1` and `classify`, the latest version), as
`scaledown/classify-1` on OpenRouter and as `scaledown/scaledown-classify-1` on Vercel's AI Gateway (open to a free-tier
team). 1M tokens of context, $0.05 per 1M input tokens ($0.04 through OpenRouter), output free - but every question is a
classify call of its own, so the state is billed once per question (~180-190 tokens per noul about a one-line review).

It reads text only: any other JSON in a request - an object or array state, object instructions, an object score level, an
option without a description - is a 400 / 422, and a noul's yes / no criteria are accepted but ignored. So its requests go
in **ScaleDown's protocol** (`DecisionProtocol.ScaleDown`), on its own host and - by model prefix
(`DecisionProvider.modelProtocols`) - for the `scaledown/` models of OpenRouter and Vercel, and every routine runs on it
unchanged (`decide[T]`, re-ranking, the guardrails, the OpenAI interface with system messages):

```scala
  val classify = TypeSafeServiceFactory(DecisionProviderSettings.scaleDown)       // SCALEDOWN_API_KEY
  classify.systemOne(review, questions)                                           // classify-1
  classify.systemOne(Json.arr("A scanned page.", DecisionImage(scan)), questions) // one image, as the state's document

  // the same rules on OpenRouter (and Vercel) for its scaledown/ models only
  val openRouter = TypeSafeServiceFactory(DecisionProviderSettings.openRouter)
  openRouter.systemOne(Json.obj("ticket" -> ticket), questions, DecisionModelId.openrouter_scaledown_classify_1)
```

- the state: text as it is; ScaleDown's own `{text, document, document_mime_type}` as it is (a base64 PDF, TIFF or image);
  any other JSON as its **pretty-printed** text - in a small benchmark (6 states x 3 nouls, twice) classify read compact
  JSON worst (mean error 0.12: a plain message with instructions taken for a prompt injection at 0.29-0.39),
  pretty-printed JSON best (0.08) and `key: value` lines in between (0.09)
- an image part goes into the state's `document` (an `[image 1]` marker left in the text), one per request - a second is
  refused before sending; ScaleDown reads it by OCR and vision (a scanned invoice's total at 0.97, a blue square told from
  a red one)
- the questions in text: a noul's criteria appended to its instructions ("Yes when: ...", "No when: ...", as for OpenAI's
  protocol - asked whether a message about a cat "qualifies" under an "animal" criterion, the criteria field left the
  answer at 0.49, the appended lines took it to 0.95), missing instructions and descriptions as empty strings (OpenRouter
  requires instructions), a `{label, description}` level as `label: description`

Live 2026-10-13, its launch day (`examples/typesafe/ScaleDownSmokeTest`, 13 sections: all passed through OpenRouter and
through Vercel; against ScaleDown's own API without credits, the listing passed and every request the client built came
back as a 402 "Insufficient credits" - it validates the body first, so a malformed one, an unknown model or a one-level
score is a 422): the review in 0.4-0.6 s, a guardrail flagging an injection and passing a plain question, `decide[Triage]`,
a rerank, the OpenAI interface with a system message. Latency grows with the questions - 0.4 / 0.6 / 0.9 / 2.4 s at 1 / 10
/ 40 / 129 nouls, 7-15 s at 150-175 - and a request that takes over ~29 s is a 504 (its AWS API Gateway's timeout):
200 questions timed out, and that day one call in a few took 15-30 s or timed out at random - keep requests well under
~100 questions and use the retry adapter (a 502 / 504 is retryable). The same question on the same state varied a little
between requests (0.44-0.55). Its `GET /v1/models` answers any key and lists compress, extract and summarize too, left
out by their output modalities; Extract and Summarize are chat-completion models, not integrated here.

> **TLS 1.3 only.** ScaleDown's own host refuses TLS 1.2. The Play WS engine offers TLS 1.3 since ws-client 1.1.2 (the
> JDK's default client protocols; before, its shaded AsyncHttpClient enabled only TLSv1.2 / 1.1 / 1 and the handshake
> failed with "Received close_notify during handshake"). A JVM that pins the protocols - with
> `play.shaded.ahc.org.asynchttpclient.enabledProtocols` or `jdk.tls.client.protocols` - must keep `TLSv1.3` in the list.

## Decision-model providers 🧩

Decision models are spreading fast, and most hosts copy TypeSafe's System One protocol - so the TypeSafe lib reaches them all
through **`DecisionProviderSettings`**, the decision-model counterpart of `ChatProviderSettings`. OpenAI's Decisions API has
a protocol of its own; its preset translates the questions and answers, so every routine on a decision service runs on it:

```scala
  import io.cequence.openaiscala.typesafe.domain.{DecisionProvider, DecisionModelId}
  import io.cequence.openaiscala.typesafe.service.{DecisionProviderSettings, TypeSafeServiceFactory}

  val openRouter = TypeSafeServiceFactory(DecisionProviderSettings.openRouter)   // OPENROUTER_API_KEY
  openRouter.listModels                                                         // its 17 decision models (2026-10-09)
  openRouter.systemOne(state, questions, DecisionModelId.openrouter_liquid_d1)

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
| `typeSafe` | `TYPESAFE_API_KEY` | `jev-latest`, `jev-preview` (Jev 1.14), `jev-1.13.0`, `jev-1.14.0` | the default of `TypeSafeServiceFactory()`; images as TypeSafe's assets, box questions on Jev 1.14; `typeSafeLongContext` for 262k tokens |
| `liquid` | `LIQUID_API_KEY` | `d1` (default), `d1-ultra` | at most 128 questions; both read images (sent in a top-level `images` array) |
| `perplexity` | `PERPLEXITY_API_KEY` (or `SONAR_API_KEY`) | `pplx-decider-v1.1-27b`, `pplx-decider-v1-27b` | `POST /v1/decisions`, images of any size, at most 128 questions |
| `openRouter` | `OPENROUTER_API_KEY` | Jev, d1, Perplexity's deciders (+ the `~perplexity/pplx-decider-latest` alias), OpenAI's Luna, Microsoft-Decision-1, Cloudflare's Clef (+ Flash, Omni), Solar Decide (+ Flash), Mercury Decide (+ free), Tev1, Kev 4B, NACE's Drex (1.5, 2.0), Span-01 (+ Lite), Span-02, ScaleDown's Classify-1, Jev 1.14, d1-ultra | listed with `output_modalities=decisions`; images as `image_url` parts in the state (read by Clef Omni / Flash, Perplexity's decider, Luna, d1 and Mercury Decide; the others read them as text); Span-01 takes noul questions only (Span-02 all three kinds, and object states); `scaledown/` models get ScaleDown's text-only protocol |
| `vercel` | `AI_GATEWAY_API_KEY` | `typesafe-ai/jev`, `liquid/d1`, `microsoft/microsoft-decision-1`, `openai/gpt-6-luna-decisions`, `convaiinnovations/laya` (+ free), `scaledown/scaledown-classify-1` | `https://ai-gateway.vercel.sh/typesafe/`, images in the state; a free-tier team gets Laya and ScaleDown at 5 requests per minute, the rest needs paid credits |
| `subq` | `SUBQ_API_KEY` | `subq-snap-preview` | `https://api.subq.ai/`, at most 295,957 input tokens, up to 4 images as `{mediaType, base64}` objects in a top-level `images` array |
| `scaleDown` | `SCALEDOWN_API_KEY` | `classify-1`, `classify` | `https://api.scaledown.xyz/`, `POST /v1/scaledown`, the key in `x-api-key`; text only (ScaleDown's protocol) plus one image as the state's document; TLS 1.3 only (see above) |
| `microsoftFoundry(endpoint, deployment)` | `FOUNDRY_API_KEY` | your deployment of Microsoft-Decision-1 (`decision-1` by default; `listModels` lists them) | your Foundry resource, `POST /providers/microsoft/v1/systemone`; at most 255 questions, 64k tokens, no images |
| `openAI` | `OPENAI_SCALA_CLIENT_API_KEY` (or `OPENAI_API_KEY`) | `gpt-6-luna` | OpenAI's own protocol (translated), images, at most 200 questions; a refusal arrives as `UnknownAnswer("refusal", ...)` |
| `llamaCpp` | none (`LLAMA_API_KEY` if the server has one) | whatever `llama-server` loaded | a local server, `http://127.0.0.1:8080/`; a router needs a model id from `listModels` (its decision models only); images in a top-level `images` array, for a model with a projector (else a 501) |

A `DecisionProvider` carries what differs between hosts: the protocol (System One, or OpenAI's), the base URL, key
variable and default model, the decisions path (`v1/systemone`, `v1/decisions` on Perplexity and OpenAI), how `listModels` finds the models (TypeSafe's `{"models"}` list, an OpenAI-style
`{"data"}` list with query parameters, Azure's deployment list on a Foundry resource, or a fixed list - with what each model reads, when the host says), a question cap and
whether and how the host reads images (in the state, or lifted into a top-level `images` array; both checked before sending,
with the largest image a host takes), the request id headers, fallback key variables, whether a key is needed at all and the header it goes in (`Authorization:
Bearer` unless `apiKeyHeader` says otherwise - ScaleDown's `x-api-key`), and the protocol of the models whose ids start with a
prefix (`modelProtocols`). `forProvider(provider, apiKey, timeouts)` and
`withEngine(engine, provider)` take an explicit key or a shared engine. OpenRouter's ids are in `models-supporting-json-schema`;
`createChatCompletionWithJSON` works with another host's ids too: it then sends the schema in the prompt (JSON-object mode),
and the adapter reads it back from there.

### Images through the OpenAI adapters 🖼️

Both OpenAI-shaped adapters send a picture the way the host takes it - TypeSafe's assets, image parts in the state
(Perplexity, OpenRouter, Vercel), a top-level `images` array (Liquid, llama.cpp), image objects (SubQ), the state's
document (ScaleDown) or an `input_image` (OpenAI) - and refuse it before sending when the provider reads none
(`DecisionProvider.readsImages`; `asOpenAI(service)` asks the service, `TypeSafeService.readsImages`).
[DecisionImageAdaptersSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/DecisionImageAdaptersSmokeTest.scala)
checks it live: one red circle and three yes / no questions (red? blue? a circle?) through the JSON schema adapter
(`asOpenAI(service)` + `createChatCompletionWithJSON`) and OpenAI's Decisions interface (`asOpenAIDecisions(service)` +
`createDecision` with an `input_image`). Live 2026-10-15 - P(red) / P(blue) / P(circle), the same through both adapters:

| model | host | images as | result |
|---|---|---|---|
| `jev-latest` (1.13), `jev-1.14.0` | typesafe | assets | 1.00 / 0.00 / 0.94, 0.98 / 0.02 / 0.89 |
| `pplx-decider-v1.1-27b` | perplexity | parts in the state | 1.00 / 0.00 / 1.00 |
| `gpt-6-luna` | openai | `input_image` | 1.00 / 0.00 / 1.00 |
| `d1` | liquid | `images` array | 1.00 / 0.00 / 1.00 |
| `subq-snap-preview` | subq | image objects | 0.99 / 0.01 / 0.98 |
| `inception/mercury-decide:free` | openrouter | parts in the state | 1.00 / 0.00 / 1.00 |
| `perplexity/pplx-decider-v1.1-27b` | vercel | parts in the state | 1.00 / 0.00 / 1.00 |
| `scaledown/scaledown-classify-1` | vercel | the state's document | 0.88 / 0.11 / 0.85 |
| `respan/span-01-lite:free` | openrouter | - | 400 "Respan does not accept `image_url` parts in state" |
| `convaiinnovations/laya-free` | vercel | - | 422 "That request was rejected" |
| `decision-1` (Microsoft-Decision-1) | microsoft-foundry | - | refused before sending |

Images are allowed per host, not per model: a text-only model on OpenRouter or Vercel gets the picture too. Span-01 and
Laya refuse it loudly, but on 2026-10-10 Jev, Kev, Tev1, Drex and Solar Decide (+ Flash) on OpenRouter read an image part
as text and answered near-uniformly - check what a model reads (its listing's input modalities) before sending it a
picture there.

### Execution time across hosts ⏱️

[DecisionModelsLatencyBenchmark](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/DecisionModelsLatencyBenchmark.scala)
times every decision model this library reaches, side by side: the same review with 1, 3, 10, 20 and 40 questions (a noul,
a choice and a score, topped up with nouls), timed calls one after another on one shared engine from one box in Europe, 5
timed calls per cell after an untimed warm-up, every host with a key in the environment (the local llama.cpp preset joins
when a server answers). Live 2026-10-09, 24 models, 640 calls in 10 minutes - median ms per call (min-max), the fastest at
10 questions first; `tokens` = the input tokens billed at 1 / 40 questions, so how a host reads the state (once, or once
per question); `defect` = the first noul's probability (0.9+ on the review's battery defect):

| model | host | 1 q | 3 q | 10 q | 20 q | 40 q | tokens | defect |
|---|---|---:|---:|---:|---:|---:|---|---:|
| cloudflare/clef-omni | openrouter | 198 (179-204) | 210 (183-307) | 217 (201-290) | 229 (208-335) | 281 (272-293) | 166 / 3,944 | 0.98 |
| jev-preview | typesafe | 259 (236-275) | 232 (223-251) | 218 (205-272) | 225 (218-234) | 244 (222-274) | 306 / 1,415 | 0.94 |
| jev-latest | typesafe | 237 (225-270) | 238 (225-272) | 225 (221-233) | 250 (235-262) | 269 (235-286) | 306 / 1,415 | 0.94 |
| gpt-6-luna | openai | 145 (117-1,703) | 225 (111-284) | 250 (137-393) | 160 (148-397) | 211 (173-218) | 182 / 6,449 | 1.00 |
| d1:free | liquid | 584 (215-638) ✗1 | 266 (254-266) ✗3 | 269 (259-309) ✗2 | 339 (325-385) ✗2 | 389 (358-389) ✗3 | 94 / 4,207 | 0.98 |
| d1 | liquid | 308 (224-697) | 245 (234-420) | 287 (268-406) | 332 (264-484) | 324 (316-372) | 64 / 3,007 | 0.97 |
| respan/span-01 † | openrouter | 419 (273-447) | 270 (251-293) | 276 (263-386) | 306 (273-313) | 379 (358-499) | 47 / 1,587 | 0.96 |
| respan/span-01-lite † | openrouter | 287 (253-306) | 267 (250-519) | 277 (262-317) | 281 (272-364) | 350 (307-534) | 47 / 1,587 | 0.96 |
| typesafe/jev-1.13 | openrouter | 281 (272-350) | 297 (280-331) | 293 (290-300) | 284 (270-309) | 284 (274-321) | 306 / 1,415 | 0.94 |
| ~typesafe/jev-latest | openrouter | 317 (283-388) | 278 (267-305) | 317 (276-394) | 303 (267-322) | 288 (274-307) | 306 / 1,415 | 0.94 |
| liquid/d1 | openrouter | 316 (292-409) | 353 (305-429) | 322 (314-382) | 360 (334-513) | 407 (363-525) | 64 / 3,007 | 0.98 |
| decision-1 (Microsoft-Decision-1) | microsoft-foundry | 283 (258-700) | 274 (188-279) | 341 (335-1,386) | 325 (230-744) | 360 (345-372) | 49 / 1,325 | 0.99 |
| openai/gpt-6-luna-decisions | openrouter | 195 (175-305) | 283 (178-324) | 358 (216-454) | 454 (221-515) | 576 (277-722) | 182 / 6,449 | 1.00 |
| inception/mercury-decide:free | openrouter | 273 (267-455) | 384 (328-526) | 358 (351-363) | 537 (444-599) | 729 (433-803) | 87 / 97 | 0.99 |
| subq-snap-preview ‡ | subq | 222 (220-271) | 276 (270-287) | 370 (332-380) | 385 (337-477) | 470 (448-1,018) | 116 / 2,402 | 0.96 |
| cloudflare/clef-flash | openrouter | 395 (243-465) | 290 (260-558) | 434 (321-459) | 497 (308-1,044) | 518 (441-684) | 169 / 3,986 | 0.89 |
| pplx-decider-v1.1-27b | perplexity | 290 (274-388) | 299 (294-368) | 440 (365-1,036) | 555 (459-2,840) | 876 (704-2,996) | 116 / 5,093 | 1.00 |
| perplexity/pplx-decider-v1.1-27b | openrouter | 326 (321-424) | 327 (318-352) | 455 (398-782) | 498 (490-540) | 912 (732-1,752) | 116 / 5,093 | 1.00 |
| cloudflare/clef | openrouter | 412 (371-438) | 372 (258-724) | 466 (357-587) | 529 (392-746) | 735 (537-850) | 169 / 3,986 | 0.97 |
| inception/mercury-decide | openrouter | 539 (343-598) | 455 (402-810) | 497 (440-591) | 494 (399-545) | 576 (548-682) | 89 / 97 | 0.99 |
| togethercomputer/tev1-4b-experimental | openrouter | 271 (260-290) | 325 (272-338) | 544 (507-576) | 983 (956-1,104) | 400 (at most 32 questions) | 117 / - | 0.78 |
| jaredpalmer/kev-4b | openrouter | 717 (492-757) | 696 (563-1,046) | 622 (547-790) | 735 (634-886) | 726 (637-926) | 41 / 1,125 | 0.70 |
| upstage/solar-decide-flash | openrouter | 1,090 (485-1,242) | 566 (534-868) | 683 (605-701) | 857 (696-2,780) | 1,809 (1,275-2,256) | 400 / 16,391 | 0.97 |
| upstage/solar-decide | openrouter | 550 (538-855) | 843 (464-988) | 22,468 (15,256-29,359) | skipped | skipped | 400 / - | 0.98 |
| respan/span-01-lite:free † | openrouter | 429 free-models-per-day | - | - | - | - | - | - |

† Span-01 judges with noul questions only, over a text state (an object state is a 400 unless it is a conversation trace),
so its rows are nouls only. ✗n = failed calls in the cell (not timed). ‡ SubQ Snap was timed on its launch day,
2026-10-12, in a run of its own next to Jev, whose medians that day were within ~25 ms of the table's: as fast as the flat
tier for one question, then growing to 0.47 s at 40, ~60 input tokens per extra question (the state read once).

What the table says:

- **The fast tier answers 1-10 questions in 0.2-0.3 s and stays flat to 40**: Cloudflare's Clef Omni (new on OpenRouter
  that day; a Qwen3-Omni-30B-A3B mixture of experts), Jev (direct, or through OpenRouter at +50-80 ms), OpenAI's Luna
  directly, Liquid's d1, Span-01 and Microsoft-Decision-1. They read the state once: 20-35 input tokens per extra
  question (Kev 1,125, Microsoft-Decision-1 1,325, Jev 1,415 and Span-01 1,587 tokens at 40 questions), except Luna,
  which bills the input per question (6,449 tokens at 40 - ~14x Jev's cost there) and still answers in ~0.2 s.
- **The others slow with the question count**: Perplexity's decider 0.29 → 0.88 s (direct and through OpenRouter alike),
  Luna through OpenRouter 0.20 → 0.58 s, Mercury Decide 0.27 → 0.73 s (it bills the state only: 97 tokens at 40
  questions), Clef 0.41 → 0.74 s, Clef Flash 0.40 → 0.52 s, Tev1 0.27 → 0.98 s at 20 questions (it takes at most 32 per
  request), Solar Decide Flash 1.1 → 1.8 s (~400 tokens per question), and Solar Decide ~2 s PER question - 22 s at 10,
  so the run skipped its larger counts (`budget=15`).
- **Free tiers throttle**: Liquid's `d1:free` answered 429 "receiving too many requests" on 11 of 30 calls paced 250 ms
  apart (the paid `d1` never failed), and OpenRouter's `span-01-lite:free` hit the free-models-per-day cap (Mercury
  Decide's free tier had used it up).
- A host's own API is as fast as, or faster than, OpenRouter's relay: Jev 225 vs 293-317 ms, d1 287 vs 322 ms, Luna 250
  vs 358 ms, Perplexity 440 vs 455 ms at 10 questions.

The box, the sequence and the hour all matter (Perplexity's 20-question cell has a 2.8 s outlier, Luna's first call took
1.7 s); run it again with your own questions before choosing - `runs=`, `counts=`, `only=`, `models=`, `budget=` and
`pace=` are its arguments.

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

### Execution time by context size 📏

[DecisionContextLatencyBenchmark](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/typesafe/DecisionContextLatencyBenchmark.scala)
asks every decision model one yes / no question over a state of 1k to 128k tokens (a 19-token sentence repeated, sized by
Jev's count), 3 timed calls per size after an untimed warm-up, and stops a model at the first size it refuses. TypeSafe's
Jev runs with its long-context opt-in (`DecisionProviderSettings.typeSafeLongContext`). Live 2026-10-14 from one box in
Europe, 31 models, 477 calls in 257 s - median ms per call; `own tokens` = the model's own input count at the largest
size it answered:

| model | host | 1k | 2k | 4k | 8k | 16k | 32k | 64k | 128k | own tokens |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| gpt-6-luna | openai | 226 | 266 | 243 | 252 | 165 | 431 | 463 | 661 | 128,144 |
| openai/gpt-6-luna-decisions | openrouter | 311 | 302 | 278 | 205 | 218 | 411 | 412 | 704 | 128,144 |
| respan/span-01-lite:free † | openrouter | 314 | 297 | 469 | 474 | 449 | 450 | 565 | 909 | 194,404 |
| jev-1.14.0 ‡ | typesafe | 225 | 369 | 244 | 280 | 335 | 501 | 836 | 1,854 | 128,262 |
| jev-1.13.0 ‡ | typesafe | 225 | 230 | 238 | 282 | 344 | 483 | 823 | 1,861 | 128,262 |
| subq-snap-preview | subq | 238 | 236 | 261 | 396 | 474 | 547 | 1,124 | 1,793 | 128,074 |
| nace-ai/drex-v2.0 * | openrouter | 600 | 625 | 431 | 787 | 938 | 1,158 | 1,410 | 1,749 | 23,790 |
| scaledown/classify-1 | openrouter | 630 | 297 | 524 | 454 | 590 | 860 | 1,503 | 2,203 | 128,166 |
| nace-ai/drex-v1.5 | openrouter | 664 | 633 | 634 | 726 | 975 | 1,263 | 1,818 | 3,132 | 128,005 |
| d1 | liquid | 483 | 185 | 200 | 266 | 410 | 580 | refused | - | 33,714 |
| decision-1 (Microsoft-Decision-1) | microsoft-foundry | 467 | 463 | 489 | 632 | 783 | 777 | refused | - | 32,023 |
| microsoft/microsoft-decision-1 | openrouter | 391 | 424 | 393 | 474 | 1,130 | 964 | refused | - | 32,023 |
| upstage/solar-decide-flash | openrouter | 1,324 | 762 | 957 | 1,956 | 1,213 ✗1 | 2,218 ✗2 | 4,041 ✗1 | 4,196 ✗2 | 128,363 |
| cloudflare/clef-omni | openrouter | 286 | 303 | 344 | 345 | 685 | 1,593 | 5,143 | refused | 64,000 |
| pplx-decider-v1.1-27b | perplexity | 301 | 351 | 445 | 714 | 1,391 | 2,850 | 6,330 | 17,547 | 128,074 |
| ~perplexity/pplx-decider-latest | openrouter | 339 | 369 | 480 | 716 | 1,454 | 2,833 | 6,558 | 17,428 | 128,074 |
| inception/mercury-decide:free | openrouter | 472 | 520 | 687 | 935 | 1,659 | 3,294 | 6,113 | refused | 61,205 |

† Span-01 Lite's tokenizer counts 1.5x as many tokens (194,404 at 128k). ‡ With the long-context opt-in; without it Jev
takes at most 33,024 input tokens for a one-question request. * Drex 2.0 reads only part of a long state - 9,756 tokens at 16k,
23,790 at 128k - so it is fast on less input. ✗n = failed calls (OpenRouter's credits ran out mid-run).

What the table says:

- **Flat to 128k**: OpenAI's Luna (0.23 s at 1k, 0.66 s at 128k; 0.70 s through OpenRouter) and Span-01 Lite (0.91 s at
  128k, 194k tokens by its own count).
- **Growing past ~32k**: Jev 1.13 and 1.14 alike (0.5 s at 32k, 1.85 s at 128k), SubQ Snap (1.79 s), ScaleDown's
  Classify-1 (2.2 s), Drex 1.5 (3.1 s) and Solar Decide Flash (4.2 s).
- **Steep**: Perplexity's decider doubles with each doubling of the state from 8k to 64k (2.85 s at 32k), then nearly
  triples - 17.5 s at 128k, directly and through OpenRouter alike; Mercury Decide's free tier takes 6.1 s and Clef Omni
  5.1 s at 64k.
- **Refused before 128k** - and several hosts check a characters / 4 estimate, not their tokenizer's count:
  - Liquid's d1 at 64k: 422 "over the model's input limit of 65536 tokens, images included, or 250000 bytes of text"
    (the 64k state is 340 kB).
  - Microsoft-Decision-1 at 64k, on Foundry and through OpenRouter: 422 "request is about 85068 tokens; the limit for the
    state plus all questions is 64000" - the state's 340,168 characters / 4, while it bills 19 tokens per 101-character
    sentence (32,023 at 32k) - so its limit is ~256,000 characters, ~48k tokens of plain English.
  - Clef Omni at 128k: 413 "The estimated number of input and maximum output tokens (170122) exceeded this model context
    window" - characters / 4 again. At 64k it reported exactly 64,000 input tokens where its count would be 64,137, so a
    longer state is apparently cut there.
  - Mercury Decide's free tier at 128k: 422 `context_length_exceeded` "A question exceeds the model's context length."
- Not measured: OpenRouter's paid Clef, Clef Flash, Mercury Decide, Kev, Tev1, Span-01 (+ Lite), Solar Decide, d1 and Jev
  (402 - the account ran out of credits mid-run); Liquid's `d1:free` is gone (404 "The model `d1:free` does not exist" -
  `/decisions/v1/models` lists only `d1`); Vercel's free-tier Laya refuses a state over ~512 tokens (422 "That request was
  rejected").

`sizes=`, `runs=`, `only=`, `models=`, `budget=`, `pace=`, `parallel=` and `out=` (one JSON line per model and size, for
charts) are its arguments.
