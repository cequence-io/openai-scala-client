package io.cequence.openaiscala.examples

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.domain.{
  FunctionCallSpec,
  JsonSchema,
  JsonSchemaDescription,
  JsonSchemaIgnore,
  JsonSchemaRange,
  ModelId,
  NonOpenAIModelId,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.perplexity.service.SonarServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.typesafe.domain.DecisionProvider
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeServiceFactory
}
import io.cequence.wsclient.JsonUtil
import play.api.libs.json.{Format, JsObject, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

// top-level, so Scala 2's runtime reflection reaches the case objects
object DerivedSchemaProvidersTypes {

  // declared out of alphabetical order - the schema must keep this order
  sealed trait Priority

  object Priority {
    case object urgent extends Priority
    case object high extends Priority
    case object normal extends Priority
    case object low extends Priority

    implicit val format: Format[Priority] =
      JsonUtil.enumFormat[Priority](urgent, high, normal, low)
  }

  case class Contact(
    @JsonSchemaDescription("The person's name") name: String,
    @JsonSchemaDescription("Email address - null if not given") email: Option[String],
    @JsonSchemaDescription("Phone number - null if not given") phone: Option[String]
  )

  case class Address(
    street: String,
    city: String,
    @JsonSchemaDescription("Postal code - null if not given") zip: Option[String]
  )

  @JsonSchemaDescription("A support ticket")
  case class Ticket(
    @JsonSchemaDescription("A short title") title: String,
    priority: Priority,
    @JsonSchemaDescription("The priority to escalate to - null if not stated")
    escalation: Option[Priority],
    @JsonSchemaDescription("Estimated hours - null if not stated")
    @JsonSchemaRange(0, 100)
    hours: Option[Int],
    @JsonSchemaDescription("Budget in EUR - null if not stated") budget: Option[Double],
    @JsonSchemaDescription("Whether the customer is a VIP - null if not stated")
    vip: Option[Boolean],
    @JsonSchemaDescription("Labels - null if none are given") labels: Option[Seq[String]],
    @JsonSchemaDescription("Who reported it") reporter: Contact,
    @JsonSchemaDescription("The site's address - null if not given") site: Option[Address],
    @JsonSchemaDescription("Further people to contact - empty if none") followUps: Seq[
      Contact
    ],
    // set by our code, never asked of the model
    @JsonSchemaIgnore internalId: Option[String] = None,
    @JsonSchemaIgnore source: String = "llm"
  )

  implicit val contactFormat: Format[Contact] = Json.format[Contact]
  implicit val addressFormat: Format[Address] = Json.format[Address]
  implicit val ticketFormat: Format[Ticket] = Json.using[Json.WithDefaultValues].format[Ticket]

  // for decision models: no free text
  sealed trait Team

  object Team {
    case object billing extends Team
    case object technical extends Team
    case object sales extends Team

    implicit val format: Format[Team] = JsonUtil.enumFormat[Team](billing, technical, sales)
  }

  sealed trait Topic

  object Topic {
    case object payments extends Topic
    case object login extends Topic
    case object shipping extends Topic

    implicit val format: Format[Topic] = JsonUtil.enumFormat[Topic](payments, login, shipping)
  }

  case class Triage(
    @JsonSchemaDescription("Which team should handle the ticket") team: Team,
    @JsonSchemaDescription("Is the customer angry?") angry: Option[Boolean],
    @JsonSchemaDescription("What the ticket is about") topics: Option[Seq[Topic]],
    @JsonSchemaDescription("How urgent, 1 (low) to 3 (high)")
    @JsonSchemaRange(1, 3)
    urgency: Option[Int],
    @JsonSchemaIgnore note: Option[String] = None
  )

  implicit val triageFormat: Format[Triage] = Json.format[Triage]
}

/**
 * Derived JSON schemas (1.7.0: `Option` fields required and nullable, sealed enums in
 * declaration order, `@JsonSchemaIgnore`) across the major providers and paths - a wider sweep
 * than `DerivedSchemaSmokeTest`. Groups (`groups=` to pick):
 *   - `json`: `createChatCompletionWithJSON[Ticket]`, the schema inferred, native
 *     `json_schema` forced - a sparse ticket (every `Option` must come back `None`) and a full
 *     one (every `Option` filled)
 *   - `fallback`: the same with `enforceMode = json_object` - the schema, `anyOf` included,
 *     appended to the prompt
 *   - `tools`: a `create_ticket` function whose parameters are `jsonSchemaFor[Ticket]`, the
 *     arguments read back as a `Ticket`; `tools-strict` the same function with `strict = true`
 *   - `union`: a hand-built strict `anyOf` of two objects
 *   - `decide`: decision models - a `Triage` with `Option` fields through `asOpenAI` and
 *     `decide[T]`
 *
 * Providers (`only=` to pick; skipped without their key): openai, openai-gpt6,
 * openai-responses, anthropic, anthropic-sonnet, bedrock (`AWS_BEARER_TOKEN_BEDROCK`), gemini,
 * gemini-3, groq (run with `-Djava.net.preferIPv6Addresses=true`), cerebras, mistral, xai,
 * fireworks, together, meta (`MODEL_API_KEY`), perplexity-agent, deepseek and minimax
 * (JSON-object only: `fallback`); decision hosts jev, perplexity-decider, liquid, subq,
 * openai-decisions. Every case prints OK / FAIL with what differed; exits 1 if any failed.
 * `schemas` prints the derived `Ticket` and `Triage` schemas and exits.
 */
object DerivedSchemaProvidersTest {

  import DerivedSchemaProvidersTypes._

  private val sparse = UserMessage(
    "Create the ticket: 'Printer broken'. Priority normal. Reported by Ann - no email or " +
      "phone given. Nothing else is known: no escalation, no estimate, no budget, VIP status " +
      "not stated, no labels, no site address, no follow-up contacts."
  )

  private val full = UserMessage(
    "Create the ticket: 'Server down in the Oslo office'. Priority urgent, escalate to high. " +
      "Estimated 6 hours, budget 1200.5 EUR, the customer is a VIP. Labels: infra, outage. " +
      "Reported by Bob (bob@example.com, +47 555 0100). Site: Storgata 1, Oslo, postal code " +
      "0155. Follow-up contact: Cara (cara@example.com, no phone)."
  )

  private def checkSparse(t: Ticket): Seq[String] =
    Seq(
      t.title.toLowerCase.contains("printer") -> s"title ${t.title}",
      (t.priority == Priority.normal) -> s"priority ${t.priority}",
      t.escalation.isEmpty -> s"escalation ${t.escalation}",
      t.hours.isEmpty -> s"hours ${t.hours}",
      t.budget.isEmpty -> s"budget ${t.budget}",
      t.vip.isEmpty -> s"vip ${t.vip}",
      t.labels.forall(_.isEmpty) -> s"labels ${t.labels}",
      t.reporter.name.contains("Ann") -> s"reporter ${t.reporter.name}",
      t.reporter.email.isEmpty -> s"email ${t.reporter.email}",
      t.reporter.phone.isEmpty -> s"phone ${t.reporter.phone}",
      t.site.isEmpty -> s"site ${t.site}",
      t.followUps.isEmpty -> s"followUps ${t.followUps}",
      (t.internalId.isEmpty && t.source == "llm") -> s"ignored fields ${t.internalId} ${t.source}"
    ).collect { case (false, problem) => problem }

  private def checkFull(t: Ticket): Seq[String] =
    Seq(
      t.title.toLowerCase.contains("server") -> s"title ${t.title}",
      (t.priority == Priority.urgent) -> s"priority ${t.priority}",
      t.escalation.contains(Priority.high) -> s"escalation ${t.escalation}",
      t.hours.contains(6) -> s"hours ${t.hours}",
      t.budget.exists(b => math.abs(b - 1200.5) < 0.01) -> s"budget ${t.budget}",
      t.vip.contains(true) -> s"vip ${t.vip}",
      t.labels.exists(l =>
        l.contains("infra") && l.contains("outage")
      ) -> s"labels ${t.labels}",
      t.reporter.name.contains("Bob") -> s"reporter ${t.reporter.name}",
      t.reporter.email.contains("bob@example.com") -> s"email ${t.reporter.email}",
      t.reporter.phone.exists(_.contains("555")) -> s"phone ${t.reporter.phone}",
      t.site.exists(s =>
        s.city.contains("Oslo") && s.street.contains("Storgata") && s.zip.contains("0155")
      ) -> s"site ${t.site}",
      t.followUps.exists(c =>
        c.name.contains("Cara") && c.email.contains("cara@example.com") && c.phone.isEmpty
      ) -> s"followUps ${t.followUps}",
      (t.internalId.isEmpty && t.source == "llm") -> s"ignored fields ${t.internalId} ${t.source}"
    ).collect { case (false, problem) => problem }

  private val cases = Seq(("sparse", sparse, checkSparse _), ("full", full, checkFull _))

  private case class Llm(
    name: String,
    env: String,
    model: String,
    service: () => OpenAIChatCompletionService,
    // false: JSON-object mode only
    nativeSchema: Boolean = true,
    settings: CreateChatCompletionSettings => CreateChatCompletionSettings = identity
  )

  private case class DecisionHost(
    name: String,
    env: String,
    provider: DecisionProvider
  )

  private def statusOf(ok: Boolean): String =
    if (ok)
      "OK  "
    else
      "FAIL"

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    def has(env: String) = sys.env.get(env).exists(_.nonEmpty)
    def arg(name: String) =
      args.find(_.startsWith(s"$name=")).map(_.stripPrefix(s"$name=").split(",").toSet)
    // `schemas`: print the derived schemas (as the providers get them, before adapters) and exit
    if (args.contains("schemas")) {
      import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
      println(
        Json.prettyPrint(Json.toJson(JsonSchemaReflectionHelper.jsonSchemaFor[Ticket]()))
      )
      println(
        Json.prettyPrint(Json.toJson(JsonSchemaReflectionHelper.jsonSchemaFor[Triage]()))
      )
      System.exit(0)
    }

    val only = arg("only")
    val groups =
      arg("groups").getOrElse(
        Set("json", "fallback", "tools", "tools-strict", "union", "decide")
      )

    val llms = Seq(
      Llm(
        "openai",
        "OPENAI_SCALA_CLIENT_API_KEY",
        ModelId.gpt_5_4_mini,
        () => OpenAIServiceFactory()
      ),
      Llm(
        "openai-gpt6",
        "OPENAI_SCALA_CLIENT_API_KEY",
        ModelId.gpt_6_luna,
        () => OpenAIServiceFactory(),
        settings = _.copy(reasoning_effort = Some(ReasoningEffort.low))
      ),
      Llm(
        "openai-responses",
        "OPENAI_SCALA_CLIENT_API_KEY",
        ModelId.gpt_5_4_mini,
        () => OpenAIServiceFactory().responsesAsChatCompletion
      ),
      Llm(
        "anthropic",
        "ANTHROPIC_API_KEY",
        NonOpenAIModelId.claude_haiku_5_5,
        () => AnthropicServiceFactory.asOpenAI()
      ),
      Llm(
        "anthropic-sonnet",
        "ANTHROPIC_API_KEY",
        NonOpenAIModelId.claude_sonnet_5_5,
        () => AnthropicServiceFactory.asOpenAI()
      ),
      Llm(
        "bedrock",
        "AWS_BEARER_TOKEN_BEDROCK",
        "global.anthropic.claude-haiku-5-5",
        () => AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(region = "eu-central-1")
      ),
      Llm(
        "gemini",
        "GOOGLE_API_KEY",
        NonOpenAIModelId.gemini_2_5_flash,
        () => GeminiServiceFactory.asOpenAI()
      ),
      Llm(
        "gemini-3",
        "GOOGLE_API_KEY",
        NonOpenAIModelId.gemini_3_8_flash,
        () => GeminiServiceFactory.asOpenAI(),
        settings = _.copy(reasoning_effort = Some(ReasoningEffort.low))
      ),
      Llm(
        "groq",
        "GROQ_API_KEY",
        NonOpenAIModelId.openai_gpt_oss_120b,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.groq)
      ),
      Llm(
        "cerebras",
        "CEREBRAS_API_KEY",
        NonOpenAIModelId.gpt_oss_120b,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.cerebras)
      ),
      Llm(
        "mistral",
        "MISTRAL_API_KEY",
        NonOpenAIModelId.mistral_large_4,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.mistral)
      ),
      Llm(
        "xai",
        "GROK_API_KEY",
        NonOpenAIModelId.grok_4_3,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.grok)
      ),
      Llm(
        "fireworks",
        "FIREWORKS_API_KEY",
        "accounts/fireworks/models/deepseek-v4p1-flash",
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.fireworks)
      ),
      Llm(
        "together",
        "TOGETHERAI_API_KEY",
        NonOpenAIModelId.openai_gpt_oss_120b,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.togetherAI)
      ),
      Llm(
        "meta",
        "MODEL_API_KEY",
        NonOpenAIModelId.muse_spark_1_3_contributor,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.meta)
      ),
      Llm(
        "perplexity-agent",
        "PERPLEXITY_API_KEY",
        "openai/gpt-5.4-mini",
        () => SonarServiceFactory.agentAsOpenAI(sys.env("PERPLEXITY_API_KEY"))
      ),
      Llm(
        "deepseek",
        "DEEPSEEK_API_KEY",
        "deepseek-flash",
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.deepseek),
        nativeSchema = false
      ),
      Llm(
        "minimax",
        "MINIMAX_API_KEY",
        "MiniMax-M3.1-Flash",
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.minimax),
        nativeSchema = false
      )
    ).filter(llm => has(llm.env) && only.forall(_.contains(llm.name)))

    val decisionHosts = Seq(
      DecisionHost("jev", "TYPESAFE_API_KEY", DecisionProviderSettings.typeSafe),
      DecisionHost(
        "perplexity-decider",
        "PERPLEXITY_API_KEY",
        DecisionProviderSettings.perplexity
      ),
      DecisionHost("liquid", "LIQUID_API_KEY", DecisionProviderSettings.liquid),
      DecisionHost("subq", "SUBQ_API_KEY", DecisionProviderSettings.subq),
      DecisionHost(
        "openai-decisions",
        "OPENAI_SCALA_CLIENT_API_KEY",
        DecisionProviderSettings.openAI
      )
    ).filter(host => has(host.env) && only.forall(_.contains(host.name)))

    // one line per case: OK / FAIL, provider, group, case, details
    def line(
      provider: String,
      group: String,
      caseName: String,
      problems: Seq[String],
      details: String
    ): (String, Boolean) = {
      val ok = problems.isEmpty
      val what =
        if (ok)
          details
        else
          problems.mkString("; ")
      (f"${statusOf(ok)} $provider%-18s $group%-8s $caseName%-7s ${what.take(400)}", ok)
    }

    def failed(e: Throwable) =
      Seq(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("").take(300)}")

    def ticketCases(
      llm: Llm,
      group: String
    )(
      ask: UserMessage => Future[Ticket]
    ): Seq[() => Future[(String, Boolean)]] =
      cases.map { case (caseName, message, check) =>
        () =>
          Try(ask(message)).fold(Future.failed, identity).transform {
            case Success(ticket) =>
              Success(line(llm.name, group, caseName, check(ticket), ticket.toString))
            case Failure(e) =>
              Success(line(llm.name, group, caseName, failed(e), ""))
          }
      }

    def llmRuns(llm: Llm): Seq[() => Future[(String, Boolean)]] = {
      lazy val service = llm.service()
      def settings =
        llm.settings(CreateChatCompletionSettings(llm.model, max_tokens = Some(6000)))

      def json(mode: ChatCompletionResponseFormatType)(message: UserMessage) =
        service.createChatCompletionWithJSON[Ticket](
          Seq(message),
          settings,
          maxRetries = Some(1),
          enforceMode = Some(mode)
        )

      val jsonRuns =
        if (groups.contains("json") && llm.nativeSchema)
          ticketCases(llm, "json")(json(ChatCompletionResponseFormatType.json_schema))
        else
          Nil

      val fallbackRuns =
        if (groups.contains("fallback"))
          ticketCases(llm, "fallback")(json(ChatCompletionResponseFormatType.json_object))
        else
          Nil

      def toolRuns(
        group: String,
        strict: Boolean
      ) =
        if (groups.contains(group) && llm.nativeSchema && llm.name != "perplexity-agent") {
          val tool = FunctionTool(
            "create_ticket",
            Some("Creates a support ticket"),
            JsonSchemaReflectionHelper.jsonSchemaFor[Ticket](),
            strict = Some(strict)
          )

          ticketCases(llm, group) { message =>
            service
              .createChatToolCompletion(
                Seq(
                  UserMessage(
                    s"${message.content}\nCall the create_ticket function with the ticket."
                  )
                ),
                Seq(tool),
                settings = settings
              )
              .map { response =>
                response.choices.head.message.tool_calls.collectFirst {
                  case (_, call: FunctionCallSpec) => call.arguments
                } match {
                  case Some(arguments) => Json.parse(arguments).as[Ticket]
                  case None =>
                    sys.error(s"no tool call: '${response.choices.head.message.content}'")
                }
              }
          }
        } else
          Nil

      val circle = JsonSchema.Object(Seq("radius" -> JsonSchema.Number()), Seq("radius"))
      val square = JsonSchema.Object(Seq("side" -> JsonSchema.Number()), Seq("side"))
      val shapes = JsonSchemaDef(
        "shapes",
        strict = true,
        Left(
          JsonSchema.Object(
            Seq(
              "shapes" -> JsonSchema.Array(
                JsonSchema.AnyOf(Seq(circle, square), Some("A circle or a square"))
              )
            ),
            Seq("shapes")
          )
        )
      )

      val unionRuns =
        if (groups.contains("union") && llm.nativeSchema)
          Seq { () =>
            Try(
              service.createChatCompletionWithJSON[JsObject](
                Seq(
                  UserMessage(
                    "List the shapes, in order: a circle of radius 2, a square of side 3."
                  )
                ),
                settings.copy(jsonSchema = Some(shapes)),
                maxRetries = Some(1),
                enforceMode = Some(ChatCompletionResponseFormatType.json_schema)
              )
            ).fold(Future.failed, identity).transform {
              case Success(json) =>
                val shapes = (json \ "shapes").asOpt[Seq[JsObject]].getOrElse(Nil).map(_.value)
                val right = shapes match {
                  case Seq(first, second) =>
                    first.get("radius").exists(_.asOpt[Double].contains(2d)) &&
                    second.get("side").exists(_.asOpt[Double].contains(3d))
                  case _ => false
                }
                val problems =
                  if (right)
                    Nil
                  else
                    Seq(s"shapes $json")
                Success(line(llm.name, "union", "shapes", problems, json.toString))
              case Failure(e) =>
                Success(line(llm.name, "union", "shapes", failed(e), ""))
            }
          }
        else
          Nil

      val runs = jsonRuns ++ fallbackRuns ++ toolRuns("tools", strict = false) ++
        toolRuns("tools-strict", strict = true) ++ unionRuns
      // close the service after its last case
      runs.zipWithIndex.map { case (run, i) =>
        () =>
          run().andThen { case _ =>
            if (i == runs.size - 1)
              service.close()
          }
      }
    }

    val triageState = "You charged me twice this month and I still can't log in!!! Fix it now."

    def checkTriage(t: Triage): Seq[String] =
      Seq(
        (t.team == Team.billing || t.team == Team.technical) -> s"team ${t.team}",
        t.angry.contains(true) -> s"angry ${t.angry}",
        t.topics.exists(topics =>
          topics.contains(Topic.payments) || topics.contains(Topic.login)
        ) -> s"topics ${t.topics}",
        t.urgency.exists(u => u >= 1 && u <= 3) -> s"urgency ${t.urgency}",
        t.note.isEmpty -> s"note ${t.note}"
      ).collect { case (false, problem) => problem }

    def decisionRuns(host: DecisionHost): Seq[() => Future[(String, Boolean)]] =
      if (groups.contains("decide"))
        Seq(
          { () =>
            val service = TypeSafeServiceFactory.asOpenAI(host.provider)
            Try(
              service.createChatCompletionWithJSON[Triage](
                Seq(UserMessage(triageState)),
                CreateChatCompletionSettings(host.provider.defaultModel)
              )
            ).fold(Future.failed, identity)
              .transform {
                case Success(t) =>
                  Success(line(host.name, "decide", "asOpenAI", checkTriage(t), t.toString))
                case Failure(e) =>
                  Success(line(host.name, "decide", "asOpenAI", failed(e), ""))
              }
              .andThen { case _ => service.close() }
          },
          { () =>
            val service = TypeSafeServiceFactory(host.provider)
            Try(service.decide[Triage](triageState))
              .fold(Future.failed, identity)
              .transform {
                case Success(d) =>
                  Success(
                    line(host.name, "decide", "decide", checkTriage(d.value), d.value.toString)
                  )
                case Failure(e) => Success(line(host.name, "decide", "decide", failed(e), ""))
              }
              .andThen { case _ => service.close() }
          }
        )
      else
        Nil

    val runs = llms.flatMap(llmRuns) ++ decisionHosts.flatMap(decisionRuns)

    // one after the other, so the lines come in order
    val all = runs.foldLeft(Future.successful(Seq.empty[(String, Boolean)])) {
      (
        acc,
        run
      ) =>
        acc.flatMap(lines => run().map { result => println(result._1); lines :+ result })
    }

    val lines = Try(Await.result(all, 40.minutes)).getOrElse(Nil)
    val failures = lines.count(!_._2)
    println(s"${lines.size - failures} of ${lines.size} passed")
    System.exit(
      if (failures == 0 && lines.nonEmpty)
        0
      else
        1
    )
  }
}
