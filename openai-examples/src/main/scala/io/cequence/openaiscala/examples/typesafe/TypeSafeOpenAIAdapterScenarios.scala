package io.cequence.openaiscala.examples.typesafe

import akka.actor.ActorSystem
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.typesafe.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.typesafe.domain.{SystemOneResponse, TypeSafeModelId}
import io.cequence.openaiscala.typesafe.service.{TypeSafeChatMapping, TypeSafeServiceFactory}
import play.api.libs.json.{Format, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Jev through the OpenAI chat-completion interface, scenario by scenario: how to call it and
 * what happens when. Each scenario prints what the adapter will send (via
 * `TypeSafeChatMapping`, no request needed) and then the live outcome - an answer, a warning
 * or the exact refusal. Requires `TYPESAFE_API_KEY`.
 *
 * The one rule: `response_format_type` must be `json_schema` with a closed-vocabulary schema.
 * The schema is the questions, the messages are the state, the answers come back as a JSON
 * document of the schema (and the raw `SystemOneResponse` in `originalResponse`).
 */
object TypeSafeOpenAIAdapterScenarios {

  private val ticket =
    "I've been trying to connect my Stripe account for 3 days and it keeps failing. " +
      "I'm losing sales. Please help ASAP."

  private val triageSchema = JsonSchemaDef(
    name = "triage",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "department" -> JsonSchema.String(
            description = Some("Which team should handle this"),
            `enum` = Seq("billing", "technical", "sales")
          ),
          "is_urgent" -> JsonSchema.Boolean(Some("The message conveys time pressure")),
          "frustration" -> JsonSchema.Integer(
            Some("How frustrated the customer appears, 1 calm .. 5 furious"),
            minimum = Some(1),
            maximum = Some(5)
          ),
          "topics" -> JsonSchema.Array(
            JsonSchema.String(`enum` = Seq("payments", "integration", "pricing")),
            description = Some("What the message is about")
          )
        ),
        required = Seq("department", "is_urgent", "frustration", "topics")
      )
    )
  )

  private def jsonSchemaSettings(schema: JsonSchemaDef = triageSchema) =
    CreateChatCompletionSettings(
      model = TypeSafeModelId.jev_latest,
      response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
      jsonSchema = Some(schema)
    )

  private case class Triage(
    department: String,
    is_urgent: Boolean,
    frustration: Int,
    topics: Seq[String]
  )

  private implicit val triageFormat: Format[Triage] = Json.format[Triage]

  def main(args: Array[String]): Unit = {
    // the actor system only serves the JSON helper's retry scheduler
    implicit val system: ActorSystem = ActorSystem()
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher
    val service = TypeSafeServiceFactory.asOpenAI()

    def scenario(
      title: String,
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    )(
      call: => Future[String]
    ): Future[Unit] = {
      println(s"\n=== $title ===")
      Try(TypeSafeChatMapping.toState(messages)).foreach(state =>
        println(s"state     : ${Json.stringify(state)}")
      )
      settings.jsonSchema.foreach(schema =>
        Try(TypeSafeChatMapping.toQuestions(schema)).foreach(qs =>
          println(s"questions : ${qs.keys.toSeq.sorted.mkString(", ")}")
        )
      )
      call.transform {
        case Success(outcome) => println(s"outcome   : $outcome"); Success(())
        case Failure(e) =>
          println(s"refused   : ${e.getClass.getSimpleName}: ${e.getMessage.take(600)}")
          Success(())
      }
    }

    def plainCall(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[String] =
      service.createChatCompletion(messages, settings).map { r =>
        s"${r.model} ${r.contentHead}  (usage ${r.usage.map(_.prompt_tokens).getOrElse(0)} in)"
      }

    val all = for {
      _ <- {
        val m = Seq(UserMessage(ticket)); val s = jsonSchemaSettings()
        scenario("1. one user message + json_schema: the message IS the state", m, s)(
          plainCall(m, s)
        )
      }

      _ <- {
        val m = Seq(
          UserMessage(
            Json.stringify(
              Json.obj(
                "subject" -> "Stripe connection",
                "body" -> ticket,
                "customer" -> Json.obj("tier" -> "gold", "open_tickets" -> 3)
              )
            )
          )
        )
        val s = jsonSchemaSettings()
        scenario("2. a JSON user message is embedded as JSON (structured state)", m, s)(
          plainCall(m, s)
        )
      }

      _ <- {
        val m = Seq(
          SystemMessage(
            "You triage tickets for a payments platform. Anything mentioning Stripe goes to " +
              "'sales' - the partnerships desk owns that relationship."
          ),
          UserMessage(ticket)
        )
        val s = jsonSchemaSettings()
        scenario(
          "3. a system message becomes the state's `instructions` - and is followed",
          m,
          s
        )(
          plainCall(m, s)
        )
      }

      _ <- {
        val m = Seq(
          SystemMessage("You triage support tickets."),
          UserMessage("My Stripe connection fails."),
          AssistantMessage("Sorry to hear that. Since when?"),
          UserMessage("Three days. I'm losing sales, fix it ASAP or I'll cancel.")
        )
        val s = jsonSchemaSettings()
        scenario("4. several turns become a `conversation`", m, s)(plainCall(m, s))
      }

      _ <- {
        val m = Seq(UserMessage(ticket)); val s = jsonSchemaSettings()
        scenario("5. the calibrated detail rides in originalResponse", m, s) {
          service.createChatCompletion(m, s).map { r =>
            r.originalResponse.collect { case sys: SystemOneResponse =>
              val d = sys.choice("department")
              val f = sys.score("frustration")
              f"department=${d.choice} ${d.ranked.map { case (k, p) => f"$k=$p%.2f" }.mkString(" ")}; " +
                f"frustration expected=${f.score}%.2f most likely level=${f.mostLikelyLevel} " +
                f"confidence=${f.confidence}%.2f; request ${sys.requestId.getOrElse("-")}"
            }.getOrElse("no SystemOneResponse?")
          }
        }
      }

      _ <- {
        val m = Seq(UserMessage(ticket))
        val strict = jsonSchemaSettings().setTypeSafeNoulThreshold(0.9)
        scenario(
          "6. the noul threshold decides where booleans (and multi-select options) flip",
          m,
          strict
        ) {
          for {
            a <- service.createChatCompletion(m, jsonSchemaSettings())
            b <- service.createChatCompletion(m, strict)
          } yield s"threshold 0.5 -> ${a.contentHead}\n            threshold 0.9 -> ${b.contentHead}"
        }
      }

      _ <- {
        val m = Seq(UserMessage(ticket))
        scenario(
          "7. createChatCompletionWithJSON[T] parses straight into a case class",
          m,
          jsonSchemaSettings()
        ) {
          service
            .createChatCompletionWithJSON[Triage](
              m,
              CreateChatCompletionSettings(TypeSafeModelId.jev_latest)
                .withJsonSchema(triageSchema)
            )
            .map(_.toString)
        }
      }

      _ <- {
        val m = Seq(UserMessage(ticket))
        val s = jsonSchemaSettings().copy(
          temperature = Some(0.2),
          max_tokens = Some(200),
          seed = Some(7)
        )
        scenario(
          "8. unsupported settings are dropped with ONE warning (see the log line above)",
          m,
          s
        )(
          plainCall(m, s)
        )
      }

      _ <- {
        val m = Seq(UserMessage(ticket))
        val logged =
          OpenAIServiceAdapters.forChatCompletionService.log(service, "jev", println(_))
        scenario("9. the usual adapters compose (here: log)", m, jsonSchemaSettings()) {
          logged.createChatCompletion(m, jsonSchemaSettings()).map(_.contentHead)
        }
      }

      _ <- {
        val m = Seq(UserMessage(ticket));
        val s = CreateChatCompletionSettings(TypeSafeModelId.jev_latest)
        scenario("10. WHAT HAPPENS WHEN: no json_schema -> refused before any I/O", m, s)(
          plainCall(m, s)
        )
      }

      _ <- {
        val m = Seq(UserMessage(ticket))
        val s = jsonSchemaSettings(
          JsonSchemaDef(
            "summary",
            strict = true,
            structure = Left(
              JsonSchema.Object(
                Seq(
                  "summary" -> JsonSchema.String(Some("One-line summary")),
                  "priority" -> JsonSchema.Integer(Some("1..5")),
                  "department" -> JsonSchema.String(`enum` = Seq("billing", "technical"))
                )
              )
            )
          )
        )
        scenario(
          "11. WHAT HAPPENS WHEN: free-form / unbounded properties -> refused, every path named",
          m,
          s
        )(
          plainCall(m, s)
        )
      }

      _ <- {
        val m = Seq(UserMessage(ticket)); val s = jsonSchemaSettings().copy(n = Some(2))
        scenario("12. WHAT HAPPENS WHEN: n > 1 -> refused", m, s)(plainCall(m, s))
      }

      _ <- {
        val m = Seq(
          UserSeqMessage(
            Seq(TextContent(ticket), ImageURLContent("data:image/png;base64,AAAA"))
          )
        )
        scenario(
          "13. WHAT HAPPENS WHEN: image content -> refused (state is text / JSON only)",
          m,
          jsonSchemaSettings()
        )(
          plainCall(m, jsonSchemaSettings())
        )
      }

      _ <- {
        val m = Seq(UserMessage(ticket))
        scenario(
          "14. WHAT HAPPENS WHEN: tools -> refused (Jev decides, it does not call)",
          m,
          jsonSchemaSettings()
        ) {
          service
            .createChatToolCompletion(
              m,
              tools = Seq(
                AssistantTool.FunctionTool(
                  "escalate",
                  parameters = JsonSchema.Object(Seq("reason" -> JsonSchema.String()))
                )
              ),
              settings = jsonSchemaSettings()
            )
            .map(_.toString)
        }
      }

      _ <- {
        val m = Seq(UserMessage(ticket)); val s = jsonSchemaSettings().copy(model = "jev-nope")
        scenario(
          "15. WHAT HAPPENS WHEN: unknown model -> the API's 400, as a client exception",
          m,
          s
        )(
          plainCall(m, s)
        )
      }
    } yield ()

    try Await.result(all, 5.minutes)
    finally {
      service.close()
      Await.result(system.terminate(), 10.seconds)
    }

  }
}
