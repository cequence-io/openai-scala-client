package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{
  JsonSchemaDescription,
  JsonSchemaRange,
  ModelId,
  NonOpenAIModelId,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import play.api.libs.json.{Format, JsObject, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * `createChatCompletionWithJSON[T]` with NO `jsonSchema` in the settings: the schema is
 * inferred from the case class ([[InferredJsonSchema]]) - named after it, with its
 * `@JsonSchemaDescription` / `@JsonSchemaRange` annotations, strict unless a field is
 * optional.
 *
 *   - `Triage` (a boolean, an `Enumeration`, a 1..5 range) on gpt-5.4-mini, Claude Haiku 5.5,
 *     Gemini 2.5 Flash-Lite and Jev - the decision model plans the inferred schema into
 *     questions;
 *   - `City` with an `Option` field on gpt-5.4-mini - a non-strict schema;
 *   - a `JsObject` call - still asked without a schema.
 *
 * Each line shows the schema the helper sent. Providers whose key is missing are skipped;
 * exits 1 when a case fails.
 */
object CreateChatCompletionJsonInferredSchema {

  object Team extends Enumeration {
    val Billing, Technical, Sales = Value
  }
  implicit val teamFormat: Format[Team.Value] = Json.formatEnum(Team)

  @JsonSchemaDescription("How a support ticket is routed")
  final case class Triage(
    @JsonSchemaDescription("Does the customer want their money back?") refund: Boolean,
    team: Team.Value,
    @JsonSchemaDescription("1 = can wait, 5 = urgent") @JsonSchemaRange(1, 5) urgency: Int
  )
  implicit val triageFormat: Format[Triage] = Json.format[Triage]

  final case class City(
    city: String,
    country: String,
    @JsonSchemaDescription("The population in millions") populationMillions: Double,
    @JsonSchemaDescription("A well-known nickname, if the city has one") nickname: Option[
      String
    ]
  )
  implicit val cityFormat: Format[City] = Json.format[City]

  private val ticket = Seq(
    UserMessage("Route this ticket: I was charged twice for my plan and want my money back.")
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val scheduler: Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    def has(env: String) = sys.env.get(env).exists(_.nonEmpty)

    // the schema each call sent, by the adapter in front of the provider
    @volatile var sent: Option[JsonSchemaDef] = None
    def recording(service: OpenAIChatCompletionService): OpenAIChatCompletionService =
      OpenAIServiceAdapters.forChatCompletionService.chatCompletionInput(
        identity,
        settings => { sent = settings.jsonSchema; settings }
      )(service)

    def schemaLabel = sent.fold("no schema")(d => s"schema '${d.name}' strict=${d.strict}")

    def run[T](
      label: String,
      call: => Future[T]
    )(
      check: T => Boolean
    ): Future[(String, Boolean)] = {
      sent = None
      call.transform { result =>
        val (ok, outcome) = result match {
          case Success(value) => (check(value), value.toString)
          case Failure(e) =>
            (false, s"FAIL ${e.getClass.getSimpleName}: ${e.getMessage.take(160)}")
        }
        Success(
          f"${if (ok) "OK  " else "FAIL"} $label%-44s $schemaLabel%-32s -> $outcome" -> ok
        )
      }
    }

    val triageOk: Triage => Boolean = t => t.refund && t.team == Team.Billing

    val cases: Seq[() => Future[(String, Boolean)]] =
      (if (has("OPENAI_SCALA_CLIENT_API_KEY")) {
         val openAI = recording(OpenAIServiceFactory())
         Seq(
           () =>
             run(
               "gpt-5.4-mini: Triage",
               openAI.createChatCompletionWithJSON[Triage](
                 ticket,
                 CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
               )
             )(t => triageOk(t) && sent.exists(d => d.name == "Triage" && d.strict)),
           () =>
             run(
               "gpt-5.4-mini: City (an Option field)",
               openAI.createChatCompletionWithJSON[City](
                 Seq(UserMessage("Tell me about the capital of Norway.")),
                 CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
               )
             )(c =>
               c.city.contains("Oslo") && sent.exists(d => d.name == "City" && !d.strict)
             ),
           () =>
             run(
               "gpt-5.4-mini: JsObject",
               openAI.createChatCompletionWithJSON[JsObject](
                 Seq(
                   UserMessage(
                     "Answer only with a JSON object holding the capital of Norway under 'city'."
                   )
                 ),
                 CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
               )
             )(json =>
               (json \ "city").asOpt[String].exists(_.contains("Oslo")) && sent.isEmpty
             )
         )
       } else Nil) ++
        (if (has("ANTHROPIC_API_KEY")) {
           val anthropic = recording(AnthropicServiceFactory.asOpenAI())
           Seq(() =>
             run(
               "claude-haiku-5-5: Triage",
               anthropic.createChatCompletionWithJSON[Triage](
                 ticket,
                 CreateChatCompletionSettings(
                   NonOpenAIModelId.claude_haiku_5_5,
                   max_tokens = Some(4000)
                 )
               )
             )(t => triageOk(t) && sent.exists(_.name == "Triage"))
           )
         } else Nil) ++
        (if (has("GOOGLE_API_KEY")) {
           val gemini = recording(GeminiServiceFactory.asOpenAI())
           Seq(() =>
             run(
               "gemini-2.5-flash-lite: Triage",
               gemini.createChatCompletionWithJSON[Triage](
                 ticket,
                 CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash_lite)
               )
             )(t => triageOk(t) && sent.exists(_.name == "Triage"))
           )
         } else Nil) ++
        (if (has("TYPESAFE_API_KEY")) {
           val jev = recording(TypeSafeServiceFactory.asOpenAI())
           Seq(() =>
             run(
               "jev-latest: Triage (a decision model)",
               jev.createChatCompletionWithJSON[Triage](
                 ticket,
                 CreateChatCompletionSettings(NonOpenAIModelId.jev_latest)
               )
             )(t => triageOk(t) && sent.exists(_.name == "Triage"))
           )
         } else Nil)

    val all = cases.foldLeft(Future.successful(Seq.empty[(String, Boolean)])) {
      (
        acc,
        next
      ) =>
        acc.flatMap(lines => next().map(lines :+ _))
    }

    val lines = Try(Await.result(all, 10.minutes)).getOrElse(Nil)
    lines.foreach(line => println(line._1))
    val failed = lines.count(!_._2)
    println(s"${lines.size - failed} of ${lines.size} passed")
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failed == 0 && lines.nonEmpty) 0 else 1)
  }
}
