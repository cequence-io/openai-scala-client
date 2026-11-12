package io.cequence.openaiscala.examples

import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  JsonSchemaDescription,
  JsonSchemaIgnore,
  ModelId,
  NonOpenAIModelId,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import io.cequence.wsclient.JsonUtil
import play.api.libs.json.{Format, JsObject, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

// top-level, so Scala 2's runtime reflection reaches the case objects
object DerivedSchemaTypes {

  sealed trait ChangeKind

  object ChangeKind {
    case object replacement extends ChangeKind
    case object addition extends ChangeKind
    case object deletion extends ChangeKind
    case object comment extends ChangeKind

    val values: Seq[ChangeKind] = Seq(replacement, addition, deletion, comment)
    implicit val format: Format[ChangeKind] = JsonUtil.enumFormat[ChangeKind](values: _*)
  }

  case class Location(
    section: String,
    page: Option[Int]
  )

  case class LineChange(
    @JsonSchemaDescription("What the change does") kind: ChangeKind,
    @JsonSchemaDescription("The first line affected") fromLine: Int,
    @JsonSchemaDescription("The new text - null for a deletion or a pure comment")
    text: Option[String],
    @JsonSchemaDescription("A review comment - null if none was given") comment: Option[
      String
    ],
    @JsonSchemaDescription("Where the change applies - null if not stated")
    location: Option[Location],
    @JsonSchemaDescription("Tags - null if none were given") tags: Option[Seq[String]],
    // set by our code, never asked of the model
    @JsonSchemaIgnore style: Option[String] = None
  )

  case class LineChanges(changes: Seq[LineChange])

  implicit val locationFormat: Format[Location] = Json.format[Location]
  implicit val lineChangeFormat: Format[LineChange] = Json.format[LineChange]
  implicit val lineChangesFormat: Format[LineChanges] = Json.format[LineChanges]

  // a decision model answers every question - its Option fields come back filled
  sealed trait Team

  object Team {
    case object billing extends Team
    case object technical extends Team
    case object sales extends Team

    implicit val format: Format[Team] = JsonUtil.enumFormat[Team](billing, technical, sales)
  }

  case class Triage(
    @JsonSchemaDescription("Which team should handle the ticket") team: Team,
    @JsonSchemaDescription("Is the customer angry?") angry: Option[Boolean],
    @JsonSchemaIgnore note: Option[String] = None
  )

  implicit val triageFormat: Format[Triage] = Json.format[Triage]
}

/**
 * Derived JSON schemas end to end (1.7.0): an `Option` field is a REQUIRED field that may be
 * null (`JsonSchema.Nullable`, written as `anyOf` with null), so the JSON helper's inferred
 * schema is `strict`; a sealed enum keeps its declaration order; `@JsonSchemaIgnore` leaves a
 * field out. Each provider gets `createChatCompletionWithJSON[LineChanges]` with NO schema in
 * the settings (inferred from the type) and native `json_schema` forced (`enforceMode`), on
 * two edit instructions - one with nothing optional given (the Options must come back `None`),
 * one with everything given. Jev (a decision model) answers a `Triage` instead (no free text).
 * Each LLM also answers a general union: shapes as `anyOf` of a circle and a square object, in
 * a strict schema (Gemini gets its own `anyOf`).
 *
 * Gemini runs on 2.5 Flash: 2.5 Flash-Lite filled the nullable `location` of the first change
 * with a placeholder (`{"section": "unknown"}`) 2 of 3 times, raw with `nullable: true` too
 * (2026-11-03; 2.5 Flash answered null 3 of 3).
 *
 * Providers whose key is missing are skipped. Groq refuses this machine's IPv4 - run with
 * `-Djava.net.preferIPv6Addresses=true`; Bedrock needs `AWS_BEARER_TOKEN_BEDROCK`. Exits 1
 * when a case fails.
 */
object DerivedSchemaSmokeTest {

  import DerivedSchemaTypes._

  private val instructions = UserMessage(
    """Turn these edit instructions into changes, in order:
      |1. Line 4: replace the text with "The parties agree." Nothing else is given.
      |2. Line 9: delete it, with the comment "duplicate clause"; it is in section Terms on
      |   page 3; tags: legal, cleanup.""".stripMargin
  )

  private def checkChanges(changes: LineChanges): Option[String] = {
    val problems = changes.changes match {
      case Seq(first, second) =>
        Seq(
          (first.kind == ChangeKind.replacement) -> s"1st kind ${first.kind}",
          (first.fromLine == 4) -> s"1st line ${first.fromLine}",
          first.text.exists(_.contains("parties agree")) -> s"1st text ${first.text}",
          first.comment.isEmpty -> s"1st comment ${first.comment}",
          first.location.isEmpty -> s"1st location ${first.location}",
          first.tags.forall(_.isEmpty) -> s"1st tags ${first.tags}",
          (second.kind == ChangeKind.deletion) -> s"2nd kind ${second.kind}",
          (second.fromLine == 9) -> s"2nd line ${second.fromLine}",
          second.comment.exists(_.contains("duplicate")) -> s"2nd comment ${second.comment}",
          second.location.exists(l =>
            l.section.contains("Terms") && l.page.contains(3)
          ) -> s"2nd location ${second.location}",
          second.tags.exists(t => t.contains("legal") && t.contains("cleanup")) ->
            s"2nd tags ${second.tags}",
          (first.style.isEmpty && second.style.isEmpty) -> "style set"
        ).collect { case (false, problem) => problem }
      case other => Seq(s"${other.size} changes")
    }
    if (problems.isEmpty)
      None
    else
      Some(problems.mkString(", "))
  }

  // "OK   <name> <model> <outcome>", and whether it passed
  private def resultLine(
    ok: Boolean,
    name: String,
    model: String,
    outcome: String
  ): (String, Boolean) = {
    val status =
      if (ok)
        "OK  "
      else
        "FAIL"
    (f"$status $name%-16s $model%-34s $outcome", ok)
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    def has(env: String) = sys.env.get(env).exists(_.nonEmpty)
    val only = args.find(_.startsWith("only=")).map(_.stripPrefix("only=").split(",").toSet)

    val llms: Seq[(String, String, String, () => OpenAIChatCompletionService)] = Seq(
      (
        "openai",
        "OPENAI_SCALA_CLIENT_API_KEY",
        ModelId.gpt_5_4_mini,
        () => OpenAIServiceFactory()
      ),
      (
        "openai-responses",
        "OPENAI_SCALA_CLIENT_API_KEY",
        ModelId.gpt_5_4_mini,
        () => OpenAIServiceFactory().responsesAsChatCompletion
      ),
      (
        "anthropic",
        "ANTHROPIC_API_KEY",
        NonOpenAIModelId.claude_haiku_5_5,
        () => AnthropicServiceFactory.asOpenAI()
      ),
      (
        "bedrock",
        "AWS_BEARER_TOKEN_BEDROCK",
        "global.anthropic.claude-haiku-5-5",
        () => AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(region = "eu-central-1")
      ),
      (
        "gemini",
        "GOOGLE_API_KEY",
        NonOpenAIModelId.gemini_2_5_flash,
        () => GeminiServiceFactory.asOpenAI()
      ),
      (
        "groq",
        "GROQ_API_KEY",
        NonOpenAIModelId.openai_gpt_oss_120b,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.groq)
      ),
      (
        "cerebras",
        "CEREBRAS_API_KEY",
        NonOpenAIModelId.gpt_oss_120b,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.cerebras)
      ),
      (
        "mistral",
        "MISTRAL_API_KEY",
        NonOpenAIModelId.mistral_large_4,
        () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.mistral)
      )
    ).filter { case (name, env, _, _) => has(env) && only.forall(_.contains(name)) }

    def runLlm(
      name: String,
      model: String,
      factory: () => OpenAIChatCompletionService
    ): Future[(String, Boolean)] = {
      // what the JSON helper hands the service
      var sent: Option[CreateChatCompletionSettings] = None
      val service = OpenAIServiceAdapters.forChatCompletionService.chatCompletionInput(
        identity,
        settings => { sent = Some(settings); settings }
      )(factory())

      service
        .createChatCompletionWithJSON[LineChanges](
          Seq(instructions),
          CreateChatCompletionSettings(model, max_tokens = Some(4000)),
          maxRetries = Some(1),
          enforceMode = Some(ChatCompletionResponseFormatType.json_schema)
        )
        .transform { result =>
          val schema = sent.flatMap(_.jsonSchema)
          val schemaJson =
            schema
              .flatMap(_.structure.left.toOption)
              .map(Json.toJson(_).toString)
              .getOrElse("")
          val schemaProblems = Seq(
            schema.exists(_.strict) -> "not strict",
            schemaJson.contains("\"anyOf\"") -> "no anyOf",
            !schemaJson.contains("\"style\"") -> "style in the schema",
            schemaJson.contains("[\"replacement\",\"addition\",\"deletion\",\"comment\"]") ->
              "enum not in declaration order"
          ).collect { case (false, problem) => problem }

          val (ok, outcome) = result match {
            case Success(changes) =>
              val problems = schemaProblems ++ checkChanges(changes).toSeq
              val outcome =
                if (problems.isEmpty)
                  changes.toString
                else
                  problems.mkString("; ")
              (problems.isEmpty, outcome)
            case Failure(e) =>
              (false, s"${e.getClass.getSimpleName}: ${e.getMessage.take(300)}")
          }
          Success(resultLine(ok, name, model, outcome))
        }
        .andThen { case _ => service.close() }
    }

    // a general union: each shape a circle or a square
    val circle = JsonSchema.Object(Seq("radius" -> JsonSchema.Number()), Seq("radius"))
    val square = JsonSchema.Object(Seq("side" -> JsonSchema.Number()), Seq("side"))
    val shapesSchema = JsonSchemaDef(
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

    def runUnion(
      name: String,
      model: String,
      factory: () => OpenAIChatCompletionService
    ): Future[(String, Boolean)] = {
      val service = factory()
      service
        .createChatCompletionWithJSON[JsObject](
          Seq(
            UserMessage("List the shapes, in order: a circle of radius 2, a square of side 3.")
          ),
          CreateChatCompletionSettings(
            model,
            max_tokens = Some(4000),
            jsonSchema = Some(shapesSchema)
          ),
          maxRetries = Some(1),
          enforceMode = Some(ChatCompletionResponseFormatType.json_schema)
        )
        .transform { result =>
          val (ok, outcome) = result match {
            case Success(json) =>
              val shapes = (json \ "shapes").asOpt[Seq[JsObject]].getOrElse(Nil)
              val right = shapes.map(_.fields.toMap) match {
                case Seq(first, second) =>
                  first.get("radius").exists(_.as[Double] == 2) &&
                  second.get("side").exists(_.as[Double] == 3)
                case _ => false
              }
              (right, json.toString)
            case Failure(e) =>
              (false, s"${e.getClass.getSimpleName}: ${e.getMessage.take(300)}")
          }
          Success(resultLine(ok, s"$name union", model, outcome))
        }
        .andThen { case _ => service.close() }
    }

    def runJev(): Future[(String, Boolean)] = {
      val service = TypeSafeServiceFactory.asOpenAI()
      service
        .createChatCompletionWithJSON[Triage](
          Seq(UserMessage("You charged me twice this month!!! Fix it now or I cancel.")),
          CreateChatCompletionSettings(NonOpenAIModelId.jev_latest)
        )
        .transform {
          case Success(triage) =>
            val ok =
              triage.team == Team.billing && triage.angry.isDefined && triage.note.isEmpty
            Success(resultLine(ok, "jev", NonOpenAIModelId.jev_latest, triage.toString))
          case Failure(e) =>
            Success(
              (s"FAIL jev: ${e.getClass.getSimpleName}: ${e.getMessage.take(300)}", false)
            )
        }
        .andThen { case _ => service.close() }
    }

    val jev =
      if (has("TYPESAFE_API_KEY") && only.forall(_.contains("jev")))
        Seq(() => runJev())
      else
        Nil

    val runs = llms.flatMap { case (name, _, model, factory) =>
      Seq(() => runLlm(name, model, factory), () => runUnion(name, model, factory))
    } ++ jev

    // one after the other, so the lines come in order
    val all = runs.foldLeft(Future.successful(Seq.empty[(String, Boolean)])) {
      (
        acc,
        run
      ) =>
        acc.flatMap(lines => run().map(line => { println(line._1); lines :+ line }))
    }

    val lines = Try(Await.result(all, 15.minutes)).getOrElse(Nil)
    val failed = lines.count(!_._2)
    println(s"${lines.size - failed} of ${lines.size} passed")
    System.exit(
      if (failed == 0 && lines.nonEmpty)
        0
      else
        1
    )
  }
}
